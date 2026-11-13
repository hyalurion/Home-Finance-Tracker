// mnn_bridge.cpp
//
// JNI bridge between Kotlin (MnnVlmEngine) and the MNN-LLM runtime.
//
// The bridge is intentionally thin: one process-wide Llm instance is created
// from a model config.json on disk, response() is invoked with a plain prompt
// or MultimodalPrompt, and generated text is streamed back to Java through a
// custom streambuf that forwards every write to a TokenListener callback.
//
// Threading contract:
//   * All native entry points take a global mutex, so Kotlin may call them
//     from any thread, but response() blocks until generation finishes.
//   * The callback may arrive on a worker thread that was never attached to
//     the JVM, so tokens are delivered through AttachCurrentThread when needed.
//
// JNI naming contract:
//   * MnnVlmEngine, MnnVlmEngine$TokenListener and onToken(String) are kept
//     by @Keep / proguard-rules.pro. See resolveTokenMethod() below.

#include <jni.h>
#include <android/log.h>

#include <cstddef>
#include <mutex>
#include <sstream>
#include <string>
#include <streambuf>
#include <vector>

#include "llm/llm.hpp"  // MNN::Transformer::Llm, MultimodalPrompt

#define LOG_TAG "MnnBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Process-wide engine state, guarded by g_engine_mutex.
MNN::Transformer::Llm* g_llm = nullptr;
bool g_llm_loaded = false;
std::mutex g_engine_mutex;

// Resolved JNI metadata for the token listener, refreshed on every call.
// Kept in file scope so the C++ streambuf can reach them without captures.
jobject g_listener_ref = nullptr;
jmethodID g_on_token_mid = nullptr;
JavaVM* g_vm = nullptr;

/**
 * Resolves TokenListener.onToken(String) for the current call.
 *
 * R8 may rename a SAM implementation class (e.g. "wj1") together with its
 * bridge method, so try the concrete class first and fall back to the kept
 * interface class. A failed lookup must be cleared: leaving a pending JNI
 * exception here aborts the whole generation when the next JNI call runs.
 * Returning null degrades the call to non-streaming generation, never a crash.
 */
jmethodID resolveTokenMethod(JNIEnv* env, jobject listener) {
    jmethodID mid = nullptr;

    jclass concrete = env->GetObjectClass(listener);
    if (concrete != nullptr) {
        mid = env->GetMethodID(concrete, "onToken", "(Ljava/lang/String;)V");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            mid = nullptr;
        }
        env->DeleteLocalRef(concrete);
    }

    if (mid == nullptr) {
        jclass iface = env->FindClass(
            "com/chronie/homemoney/data/vlm/MnnVlmEngine$TokenListener");
        if (iface != nullptr) {
            mid = env->GetMethodID(iface, "onToken", "(Ljava/lang/String;)V");
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                mid = nullptr;
            }
            env->DeleteLocalRef(iface);
        } else if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }

    if (mid == nullptr) {
        LOGE("TokenListener.onToken(String) not found; token streaming disabled");
    }
    return mid;
}

/**
 * Zero-buffering streambuf: every write from MNN's response() is forwarded
 * to the Kotlin TokenListener immediately, which is what makes tokens show up
 * on screen while the model is still generating. The previous 256-byte buffer
 * only flushed on overflow or explicit sync(), so short token writes were
 * delivered in large late bursts (or only at the very end).
 *
 * A tiny UTF-8 tail buffer keeps multi-byte characters intact when MNN splits
 * a token in the middle of a CJK character. Complete prefixes are emitted at
 * once; the incomplete tail is flushed by sync().
 */
class ListenerStreambuf : public std::streambuf {
public:
    ListenerStreambuf() = default;
    ~ListenerStreambuf() override { sync(); }

    // Non-copyable
    ListenerStreambuf(const ListenerStreambuf&) = delete;
    ListenerStreambuf& operator=(const ListenerStreambuf&) = delete;

    /** Full text seen so far; used when LlmContext.generate_str is empty. */
    const std::string& text() const { return full_; }

protected:
    int_type overflow(int_type ch) override {
        if (ch != traits_type::eof()) {
            const char c = static_cast<char>(ch);
            pushBytes(&c, 1);
        }
        return traits_type::not_eof(ch);
    }

    std::streamsize xsputn(const char* data, std::streamsize count) override {
        if (count > 0 && data != nullptr) {
            pushBytes(data, static_cast<size_t>(count));
        }
        return count;
    }

    int sync() override {
        if (!pending_.empty()) {
            emit(pending_.data(), pending_.size());
            pending_.clear();
        }
        return 0;
    }

private:
    /** Appends bytes and emits the longest complete UTF-8 prefix. */
    void pushBytes(const char* data, size_t len) {
        pending_.append(data, len);
        size_t safe = 0;
        while (safe < pending_.size()) {
            const auto lead = static_cast<unsigned char>(pending_[safe]);
            const size_t width = (lead < 0x80) ? 1
                               : (lead >= 0xF0) ? 4
                               : (lead >= 0xE0) ? 3
                               : (lead >= 0xC0) ? 2
                               : 1;
            if (safe + width > pending_.size()) break;
            safe += width;
        }
        if (safe > 0) {
            emit(pending_.data(), safe);
            pending_.erase(0, safe);
        }
    }

    /** Delivers one chunk to Java, tolerating unattached threads and throws. */
    void emit(const char* data, size_t len) {
        if (len == 0) return;
        full_.append(data, len);
        if (g_listener_ref == nullptr || g_on_token_mid == nullptr || g_vm == nullptr) return;

        JNIEnv* env = nullptr;
        bool attached = false;
        if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
            env = nullptr;
            if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK || env == nullptr) {
                LOGE("Cannot attach token callback thread to the JVM");
                return;
            }
            attached = true;
        }

        jstring chunk = env->NewStringUTF(std::string(data, len).c_str());
        if (chunk != nullptr) {
            env->CallVoidMethod(g_listener_ref, g_on_token_mid, chunk);
            env->DeleteLocalRef(chunk);
        }
        if (env->ExceptionCheck()) {
            // A throwing listener must not poison the native generation loop.
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        if (attached) g_vm->DetachCurrentThread();
    }

    std::string pending_;
    std::string full_;
};

}  // namespace

// Called once when the library is loaded, so we can cache the JavaVM.
jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" {

/**
 * Creates and loads the Llm instance from a model directory's config.json.
 *
 * @param configPath   absolute path to <modelDir>/config.json
 * @param extraConfig  JSON string merged into the engine config, e.g.
 *                     {"max_new_tokens":1024,"use_mmap":true}
 * @return 0 on success, -1 when creation failed, -2 when already created,
 *         -3 when model files are missing (runtime not bundled).
 */
JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeCreate(
        JNIEnv* env, jobject, jstring configPath, jstring extraConfig) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm != nullptr) {
        return -2;
    }

    const char* pathChars = env->GetStringUTFChars(configPath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(configPath, pathChars);

    LOGI("Creating LLM from config: %s", path.c_str());
    g_llm = MNN::Transformer::Llm::createLLM(path);
    if (g_llm == nullptr) {
        LOGE("Llm::createLLM returned null");
        return -1;
    }

    // Optional runtime tweaks supplied as a JSON string (set_config merges them).
    if (extraConfig != nullptr) {
        const char* configChars = env->GetStringUTFChars(extraConfig, nullptr);
        std::string config(configChars);
        env->ReleaseStringUTFChars(extraConfig, configChars);
        if (!config.empty()) {
            try {
                g_llm->set_config(config);
            } catch (const std::exception& e) {
                // Config tweaks are best-effort; a rejected key must not kill the session.
                LOGE("set_config failed (non-fatal): %s", e.what());
            }
        }
    }

    // Explicit load is required in the current MNN API.
    try {
        if (!g_llm->load()) {
            LOGE("Llm::load() returned false");
            delete g_llm;
            g_llm = nullptr;
            return -1;
        }
    } catch (const std::exception& e) {
        LOGE("Llm::load() threw: %s", e.what());
        delete g_llm;
        g_llm = nullptr;
        return -1;
    }

    g_llm_loaded = true;
    LOGI("LLM created and loaded successfully");
    return 0;
}

/**
 * Returns 1 when an Llm instance is alive, 0 otherwise.
 */
JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeIsCreated(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    return (g_llm != nullptr && g_llm_loaded) ? 1 : 0;
}

/**
 * Releases the Llm instance and all native resources. Safe to call repeatedly.
 */
JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeDestroy(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm != nullptr) {
        try {
            MNN::Transformer::Llm::destroy(g_llm);
        } catch (const std::exception& e) {
            LOGE("Llm::destroy() threw (non-fatal): %s", e.what());
        }
        g_llm = nullptr;
        g_llm_loaded = false;
        LOGI("LLM destroyed");
    }
}

/**
 * Runs one-shot generation. Two modes:
 *
 * 1) Multimodal (imagePaths non-empty):
 *    Images are embedded as <img>path</img> markers, matching MNN's visual
 *    model prompt convention (LLM_SUPPORT_VISION).
 *
 * 2) Text-only (imagePaths empty):
 *    Calls response(user_content, ...) directly.
 *
 * @param prompt      user prompt text
 * @param imagePaths  comma-separated absolute paths to pre-processed images
 * @param listener    optional TokenListener whose onToken(String) receives
 *                    every generated chunk (may be null)
 * @return the complete generated text, or an empty string on failure.
 */
JNIEXPORT jstring JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeChat(
        JNIEnv* env, jobject, jstring prompt, jstring imagePaths, jobject listener) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm == nullptr || !g_llm_loaded) {
        LOGE("nativeChat called before nativeCreate/load");
        return env->NewStringUTF("");
    }

    const char* promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    // Parse comma-separated image paths.
    std::vector<std::string> images;
    if (imagePaths != nullptr) {
        const char* pathsChars = env->GetStringUTFChars(imagePaths, nullptr);
        std::string pathsStr(pathsChars);
        env->ReleaseStringUTFChars(imagePaths, pathsChars);

        std::istringstream pathStream(pathsStr);
        std::string segment;
        while (std::getline(pathStream, segment, ',')) {
            // Trim whitespace.
            auto start = segment.find_first_not_of(" \t\r\n");
            auto end = segment.find_last_not_of(" \t\r\n");
            if (start != std::string::npos && end != std::string::npos) {
                images.push_back(segment.substr(start, end - start + 1));
            }
        }
    }

    // Register the listener for the duration of this call. Resolution is
    // best-effort: failure degrades to a one-shot answer instead of leaving a
    // pending JNI exception that would abort response() on its first token.
    if (listener != nullptr) {
        g_listener_ref = env->NewGlobalRef(listener);
        g_on_token_mid = resolveTokenMethod(env, listener);
        if (g_on_token_mid == nullptr && g_listener_ref != nullptr) {
            env->DeleteGlobalRef(g_listener_ref);
            g_listener_ref = nullptr;
        }
    } else {
        g_listener_ref = nullptr;
        g_on_token_mid = nullptr;
    }

    std::string result;
    bool failed = false;
    ListenerStreambuf streambuf;
    std::ostream outputStream(&streambuf);

    try {
        if (!images.empty()) {
            // <img>path</img> markers are what LLM_SUPPORT_VISION resolves.
            // They are prepended so the visual encoder sees the images before
            // the textual instruction, matching MNN's chat template.
            std::string imgPrompt;
            for (size_t i = 0; i < images.size(); ++i) {
                imgPrompt += "<img>" + images[i] + "</img>\n";
            }
            imgPrompt += promptStr;

            g_llm->response(imgPrompt, &outputStream, nullptr, -1);
        } else {
            // Text-only mode.
            g_llm->response(promptStr, &outputStream, nullptr, -1);
        }

        // Flush any incomplete UTF-8 tail still held by the streambuf.
        outputStream.flush();
        streambuf.pubsync();

        // Prefer the runtime's accumulated text, but fall back to what was
        // actually written to the stream: some MNN builds leave generate_str
        // empty even though response() streamed the whole answer.
        auto ctx = g_llm->getContext();
        if (ctx != nullptr && !ctx->generate_str.empty()) {
            result = ctx->generate_str;
        } else {
            result = streambuf.text();
        }
    } catch (const std::exception& e) {
        LOGE("response() threw: %s", e.what());
        failed = true;
    } catch (...) {
        LOGE("response() threw an unknown exception");
        failed = true;
    }

    if (g_listener_ref != nullptr) {
        env->DeleteGlobalRef(g_listener_ref);
        g_listener_ref = nullptr;
        g_on_token_mid = nullptr;
    }

    if (failed) {
        return env->NewStringUTF("");
    }

    LOGI("response() produced %zu chars", result.size());
    return env->NewStringUTF(result.c_str());
}

/**
 * Resets the conversation/KV cache. Keeps the model loaded in memory.
 */
JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeReset(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm != nullptr) {
        try {
            g_llm->reset();
        } catch (const std::exception& e) {
            LOGE("reset() failed (non-fatal): %s", e.what());
        }
    }
}

}  // extern "C"
