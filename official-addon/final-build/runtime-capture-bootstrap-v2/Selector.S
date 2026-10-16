#if defined(__x86_64__)
.text
.globl _TIOIntegrationSelector
_TIOIntegrationSelector:
    cmpq $-2, %rdi
    je _TIOIntegrationDefault
    jmp _TIOResolverBridge
#elif defined(__aarch64__)
.text
.globl _TIOIntegrationSelector
_TIOIntegrationSelector:
    cmn x0, #2
    b.eq _TIOIntegrationDefault
    b _TIOResolverBridge
#endif
