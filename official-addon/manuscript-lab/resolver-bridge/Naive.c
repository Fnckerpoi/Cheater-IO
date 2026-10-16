#include <dlfcn.h>
__attribute__((noinline)) void *NaiveLookup(void *handle, const char *name) {
    void *result = dlsym(handle, name);
    __asm__ volatile("" : "+r"(result));
    return result;
}
int bridge_collision(void) { return 333; }
