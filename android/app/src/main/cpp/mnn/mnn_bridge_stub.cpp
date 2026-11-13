// mnn_bridge_stub.cpp
//
// Fallback implementation used when the MNN prebuilt libraries are not
// present in cpp/mnn/prebuilt/<abi>/ (see scripts/build-mnn-android.ps1).
//
// Having a stub keeps the APK buildable on machines without the ~100 MB MNN
// native bundle: libmnn_bridge.so always exists, loads fine, and every call
// reports "engine unavailable" through its return value. The Kotlin layer
// surfaces this as a user-facing "model runtime missing" error instead of
// crashing with UnsatisfiedLinkError.

#include <jni.h>
#include <android/log.h>

#define LOG_TAG "MnnBridgeStub"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" {

// Always fails with the dedicated "runtime missing" code (see MnnVlmEngine).
JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeCreate(
        JNIEnv*, jobject, jstring, jstring) {
    LOGW("nativeCreate: MNN runtime not bundled in this build");
    return -3;
}

JNIEXPORT jint JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeIsCreated(JNIEnv*, jobject) {
    return 0;
}

JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeDestroy(JNIEnv*, jobject) {
}

// Returns an empty string, which the Kotlin layer treats as an error.
JNIEXPORT jstring JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeChat(
        JNIEnv* env, jobject, jstring, jstring, jobject) {
    return env->NewStringUTF("");
}

// Multi-turn chat entry point; mirrors the real bridge's signature.
JNIEXPORT jstring JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeChatTurns(
        JNIEnv* env, jobject, jobjectArray, jobjectArray, jstring, jobject,
        jstring, jint) {
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_chronie_homemoney_data_vlm_MnnVlmEngine_nativeReset(JNIEnv*, jobject) {
}

}  // extern "C"
