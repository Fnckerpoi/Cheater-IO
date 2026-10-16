#if defined(__x86_64__)
.text
.globl _TIOStageBSelector
_TIOStageBSelector:
    cmpq $-2, %rdi
    je _TIOStageBDefault
    jmp _TIOResolverBridge
#elif defined(__aarch64__)
.text
.globl _TIOStageBSelector
_TIOStageBSelector:
    cmn x0, #2
    b.eq _TIOStageBDefault
    b _TIOResolverBridge
#endif
