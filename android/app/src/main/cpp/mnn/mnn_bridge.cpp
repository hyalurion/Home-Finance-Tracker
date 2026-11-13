// mnn_bridge.cpp
//
// JNI bridge between Kotlin (MnnVlmEngine) and the MNN-LLM runtime.
//
// Two generation entry points are exposed:
//   * nativeChat       - legacy single-string prompt, kept for bill OCR.
//   * nativeChatTurns  - role-tagged ChatMessages, used by AI chat so MNN
//                        applies the model's real chat template. Flattening
//                        history into one user string made small models
//                        continue the transcript with invented [User] turns.
//
// Generated text is streamed to Java through a zero-buffering streambuf.
// All Java strings are converted through UTF-16 explicitly: NewStringUTF /
// GetStringUTFChars use *modified* UTF-8 and corrupt 4-byte code points
// (emoji and some CJK extension characters), which showed up as "??" in the
// chat UI.
//
// JNI naming contract: MnnVlmEngine, MnnVlmEngine$TokenListener and
// onToken(String) are kept by @Keep / proguard-rules.pro.

#include <jni.h>
#include <android/log.h>

#include <cstddef>
#include <cstdint>
#include <functional>
#include <mutex>
#include <sstream>
#include <string>
#include <streambuf>
#include <vector>

#include "llm/llm.hpp"  // MNN::Transformer::Llm, ChatMessages

#define LOG_TAG "MnnBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Process-wide engine state, guarded by g_engine_mutex.
MNN::Transformer::Llm* g_llm = nullptr;
bool g_llm_loaded = false;
std::mutex g_engine_mutex;

// Resolved JNI metadata for the token listener, refreshed on every call.
jobject g_listener_ref = nullptr;
jmethodID g_on_token_mid = nullptr;
JavaVM* g_vm = nullptr;

// ------------------------------------------------------------------
// UTF-8 <-> UTF-16 helpers (Java strings use UTF-16, MNN uses UTF-8)
// ------------------------------------------------------------------

void AppendCodepoint(std::string& out, uint32_t cp) {
    if (cp < 0x80) {
        out.push_back(static_cast<char>(cp));
    } else if (cp < 0x800) {
        out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp < 0x10000) {
        out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
}

std::string Utf16ToUtf8(const jchar* chars, jsize length) {
    std::string out;
    if (chars == nullptr) return out;
    out.reserve(static_cast<size_t>(length));
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length &&
            chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[++i] - 0xDC00);
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;  // lone surrogate
        }
        AppendCodepoint(out, cp);
    }
    return out;
}

std::u16string Utf8ToUtf16(const char* data, size_t length) {
    std::u16string out;
    if (data == nullptr) return out;
    out.reserve(length);
    size_t i = 0;
    while (i < length) {
        const auto lead = static_cast<unsigned char>(data[i]);
        uint32_t cp = 0;
        size_t width = 0;
        if (lead < 0x80) {
            cp = lead;
            width = 1;
        } else if (lead >= 0xC2 && lead <= 0xDF) {
            cp = lead & 0x1F;
            width = 2;
        } else if (lead >= 0xE0 && lead <= 0xEF) {
            cp = lead & 0x0F;
            width = 3;
        } else if (lead >= 0xF0 && lead <= 0xF4) {
            cp = lead & 0x07;
            width = 4;
        } else {
            out.push_back(u'\uFFFD');
            ++i;
            continue;
        }
        if (i + width > length) {
            out.push_back(u'\uFFFD');
            break;
        }
        bool valid = true;
        for (size_t k = 1; k < width; ++k) {
            const auto cont = static_cast<unsigned char>(data[i + k]);
            if ((cont & 0xC0) != 0x80) {
                valid = false;
                break;
            }
            cp = (cp << 6) | (cont & 0x3F);
        }
        if (!valid) {
            out.push_back(u'\uFFFD');
            ++i;
            continue;
        }
        i += width;
        if (cp > 0xFFFF) {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(cp));
        }
    }
    return out;
}

std::string ReadJavaString(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::string();
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return std::string();
    std::string result = Utf16ToUtf8(chars, env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return result;
}

jstring NewJavaString(JNIEnv* env, const std::string& value) {
    std::u16string utf16 = Utf8ToUtf16(value.data(), value.size());
    return env->NewString(
        reinterpret_cast<const jchar*>(utf16.data()),
        static_cast<jsize>(utf16.size()));
}

std::vector<std::string> ReadJavaStringArray(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> result;
    if (array == nullptr) return result;
    const jsize count = env->GetArrayLength(array);
    result.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto element = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        result.push_back(ReadJavaString(env, element));
        if (element != nullptr) env->DeleteLocalRef(element);
    }
    return result;
}

std::vector<std::string> ParseImagePaths(const std::string& joined) {
    std::vector<std::string> images;
    std::istringstream stream(joined);
    std::string segment;
    while (std::getline(stream, segment, ',')) {
        const auto start = segment.find_first_not_of(" \t\r\n");
        const auto end = segment.find_last_not_of(" \t\r\n");
        if (start != std::string::npos && end != std::string::npos) {
            images.push_back(segment.substr(start, end - start + 1));
        }
    }
    return images;
}

/**
 * Resolves TokenListener.onToken(String). R8 may rename a SAM implementation
 * class, so try the concrete class first and fall back to the kept interface.
 * A failed lookup MUST be cleared: a pending JNI exception aborts generation.
 */
jmethodID ResolveTokenMethod(JNIEnv* env, jobject listener) {
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
 * Zero-buffering streambuf: every write is forwarded immediately, which is
 * what makes tokens appear while the model is still generating. A tiny tail
 * buffer keeps multi-byte UTF-8 characters intact when MNN splits a token.
 */
class ListenerStreambuf : public std::streambuf {
public:
    ListenerStreambuf() = default;
    ~ListenerStreambuf() override { sync(); }

    ListenerStreambuf(const ListenerStreambuf&) = delete;
    ListenerStreambuf& operator=(const ListenerStreambuf&) = delete;

    const std::string& text() const { return full_; }

protected:
    int_type overflow(int_type ch) override {
        if (ch != traits_type::eof()) {
            const char c = static_cast<char>(ch);
            PushBytes(&c, 1);
        }
        return traits_type::not_eof(ch);
    }

    std::streamsize xsputn(const char* data, std::streamsize count) override {
        if (count > 0 && data != nullptr) {
            PushBytes(data, static_cast<size_t>(count));
        }
        return count;
    }

    int sync() override {
        if (!pending_.empty()) {
            Emit(pending_.data(), pending_.size());
            pending_.clear();
        }
        return 0;
    }

private:
    void PushBytes(const char* data, size_t len) {
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
            Emit(pending_.data(), safe);
            pending_.erase(0, safe);
        }
    }

    void Emit(const char* data, size_t len) {
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

        jstring chunk = NewJavaString(env, std::string(data, len));
        if (chunk != nullptr) {
            env->CallVoidMethod(g_listener_ref, g_on_token_mid, chunk);
            env->DeleteLocalRef(chunk);
        }
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        if (attached) g_vm->DetachCurrentThread();
    }

    std::string pending_;
    std::string full_;
};

/**
 * Common setup/teardown for one response() call. Returns the exact text that
 * was streamed (not MNN's post-processed generate_str, which can drop the
 * model's reasoning markers).
 */
template <typename Invoke>
std::string ExecuteResponse(JNIEnv* env, jobject listener, Invoke&& invoke) {
    if (listener != nullptr) {
        g_listener_ref = env->NewGlobalRef(listener);
        g_on_token_mid = ResolveTokenMethod(env, listener);
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
        invoke(&outputStream);
        outputStream.flush();
        streambuf.pubsync();
        const bool streaming = g_listener_ref != nullptr;
        const MNN::Transformer::LlmContext* ctx = g_llm->getContext();
        if (streaming && !streambuf.text().empty()) {
            result = streambuf.text();
        } else if (ctx != nullptr && !ctx->generate_str.empty()) {
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
    return failed ? std::string() : result;
}

}  // namespace

jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeCreate(
        JNIEnv* env, jobject, jstring configPath, jstring extraConfig) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm != nullptr) return -2;

    const std::string path = ReadJavaString(env, configPath);
    LOGI("Creating LLM from config: %s", path.c_str());
    g_llm = MNN::Transformer::Llm::createLLM(path);
    if (g_llm == nullptr) {
        LOGE("Llm::createLLM returned null");
        return -1;
    }

    const std::string extra = ReadJavaString(env, extraConfig);
    if (!extra.empty()) {
        try {
            g_llm->set_config(extra);
        } catch (const std::exception& e) {
            LOGE("set_config failed (non-fatal): %s", e.what());
        }
    }

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

JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeIsCreated(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    return (g_llm != nullptr && g_llm_loaded) ? 1 : 0;
}

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

/** Legacy single-string prompt path, still used by bill OCR. */
JNIEXPORT jstring JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeChat(
        JNIEnv* env, jobject, jstring prompt, jstring imagePaths, jobject listener) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm == nullptr || !g_llm_loaded) {
        LOGE("nativeChat called before nativeCreate/load");
        return NewJavaString(env, "");
    }

    std::string promptStr = ReadJavaString(env, prompt);
    const std::vector<std::string> images =
        ParseImagePaths(ReadJavaString(env, imagePaths));

    const std::string result = ExecuteResponse(env, listener, [&](std::ostream* os) {
        std::string effective = promptStr;
        if (!images.empty()) {
            std::string prefix;
            for (const auto& image : images) {
                prefix += "<img>" + image + "</img>\n";
            }
            effective = prefix + effective;
        }
        g_llm->response(effective, os, nullptr, -1);
    });

    LOGI("nativeChat produced %zu chars", result.size());
    return NewJavaString(env, result);
}

/**
 * Multi-turn path used by AI chat. Roles are real chat-template roles, so the
 * model stops at its own EOS instead of continuing a flattened transcript.
 *
 * endWith is a literal stop string (e.g. "[User]") passed straight to MNN.
 */
JNIEXPORT jstring JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeChatTurns(
        JNIEnv* env, jobject, jobjectArray roles, jobjectArray contents,
        jstring imagePaths, jobject listener, jstring endWith, jint maxNewTokens) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_llm == nullptr || !g_llm_loaded) {
        LOGE("nativeChatTurns called before nativeCreate/load");
        return NewJavaString(env, "");
    }

    const std::vector<std::string> roleList = ReadJavaStringArray(env, roles);
    const std::vector<std::string> contentList = ReadJavaStringArray(env, contents);
    const std::string endWithStr = ReadJavaString(env, endWith);
    const std::vector<std::string> images =
        ParseImagePaths(ReadJavaString(env, imagePaths));

    if (roleList.empty() || roleList.size() != contentList.size()) {
        LOGE("nativeChatTurns: role/content arrays differ in size");
        return NewJavaString(env, "");
    }

    MNN::Transformer::ChatMessages messages;
    messages.reserve(roleList.size());
    for (size_t i = 0; i < roleList.size(); ++i) {
        messages.emplace_back(roleList[i], contentList[i]);
    }

    // Vision markers belong to the most recent user turn.
    if (!images.empty()) {
        std::string prefix;
        for (const auto& image : images) {
            prefix += "<img>" + image + "</img>\n";
        }
        size_t lastUser = messages.size();
        while (lastUser > 0) {
            --lastUser;
            if (messages[lastUser].first == "user") break;
        }
        if (lastUser < messages.size() && messages[lastUser].first == "user") {
            messages[lastUser].second = prefix + messages[lastUser].second;
        } else {
            messages.emplace_back("user", prefix);
        }
    }

    const char* stop = endWithStr.empty() ? nullptr : endWithStr.c_str();
    const std::string result = ExecuteResponse(env, listener, [&](std::ostream* os) {
        g_llm->response(messages, os, stop, static_cast<int>(maxNewTokens));
    });

    LOGI("nativeChatTurns produced %zu chars", result.size());
    return NewJavaString(env, result);
}

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
