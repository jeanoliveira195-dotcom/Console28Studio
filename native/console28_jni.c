#include <jni.h>
#include <stdint.h>
#include <stddef.h>

// ABI pública do módulo Rust; há uma instância separada por sessão de reprodução/gravação.
extern void* console28_create(uint32_t sample_rate, uint32_t channels);
extern void console28_destroy(void* engine);
extern int32_t console28_reset(void* engine);
extern int32_t console28_set_settings(void* engine, uint8_t enabled, const float* args, size_t count);
extern int32_t console28_process(void* engine, int16_t* pcm, size_t count);

static void throw_error(JNIEnv* env, const char* message) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type) (*env)->ThrowNew(env, type, message);
}

JNIEXPORT jlong JNICALL
Java_com_jean_console28_audio_NativeConsole28_create(JNIEnv* env, jobject unused, jint rate, jint channels) {
    (void)unused;
    void* p = console28_create((uint32_t)rate, (uint32_t)channels);
    if (!p) throw_error(env, "Rust DSP: taxa ou canais inválidos");
    return (jlong)(intptr_t)p;
}

JNIEXPORT void JNICALL
Java_com_jean_console28_audio_NativeConsole28_destroy(JNIEnv* env, jobject unused, jlong handle) {
    (void)env; (void)unused;
    if (handle) console28_destroy((void*)(intptr_t)handle);
}

JNIEXPORT void JNICALL
Java_com_jean_console28_audio_NativeConsole28_reset(JNIEnv* env, jobject unused, jlong handle) {
    (void)unused;
    if (console28_reset((void*)(intptr_t)handle) != 0) throw_error(env, "Rust DSP: ponteiro inválido");
}

JNIEXPORT void JNICALL
Java_com_jean_console28_audio_NativeConsole28_setSettings(JNIEnv* env, jobject unused, jlong handle,
                                                            jboolean enabled, jfloatArray array) {
    (void)unused;
    if (!array || (*env)->GetArrayLength(env, array) != 8) {
        throw_error(env, "Rust DSP: oito parâmetros são necessários");
        return;
    }
    float values[8];
    (*env)->GetFloatArrayRegion(env, array, 0, 8, values);
    if ((*env)->ExceptionCheck(env)) return;
    if (console28_set_settings((void*)(intptr_t)handle, enabled ? 1 : 0, values, 8) != 0)
        throw_error(env, "Rust DSP: parâmetros inválidos");
}

JNIEXPORT void JNICALL
Java_com_jean_console28_audio_NativeConsole28_process(JNIEnv* env, jobject unused, jlong handle,
                                                        jshortArray array, jint count) {
    (void)unused;
    if (!array || count < 0 || count > (*env)->GetArrayLength(env, array)) {
        throw_error(env, "Rust DSP: tamanho do buffer inválido");
        return;
    }
    if (count == 0) return;
    jshort* samples=(*env)->GetShortArrayElements(env, array, NULL);
    if (!samples) return; // JVM já lança OOME se necessário.
    int rc=console28_process((void*)(intptr_t)handle, (int16_t*)samples, (size_t)count);
    (*env)->ReleaseShortArrayElements(env, array, samples, rc == 0 ? 0 : JNI_ABORT);
    if (rc != 0) throw_error(env, "Rust DSP: falha no processamento PCM");
}
