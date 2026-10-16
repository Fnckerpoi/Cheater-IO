#if defined(__x86_64__)
.text
.globl _TIOOwnSelector
_TIOOwnSelector:
    cmpq $-2, %rdi
    je _TIOOwnSelectDefault
    jmpq *_TIOOwnRealResolver(%rip)
#elif defined(__aarch64__)
.text
.globl _TIOOwnSelector
_TIOOwnSelector:
    cmn x0, #2
    b.eq _TIOOwnSelectDefault
    adrp x16, _TIOOwnRealResolver@PAGE
    ldr x16, [x16, _TIOOwnRealResolver@PAGEOFF]
    br x16
#endif
