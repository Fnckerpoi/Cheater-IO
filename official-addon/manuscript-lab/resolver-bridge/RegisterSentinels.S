#if defined(__x86_64__)
.text
.globl _TIOSentinelInvoke
_TIOSentinelInvoke:
    pushq %rbp
    movq %rsp, %rbp
    pushq %r12
    subq $8, %rsp
    movq %rdi, %r12
    movq $0x111, %rdi
    movq $0x222, %rsi
    movq $0x333, %rdx
    movq $0x444, %rcx
    movq $0x555, %r8
    movq $0x666, %r9
    movdqu _TIOSentinelVectors(%rip), %xmm0
    movdqu _TIOSentinelVectors+16(%rip), %xmm1
    movdqu _TIOSentinelVectors+32(%rip), %xmm2
    movdqu _TIOSentinelVectors+48(%rip), %xmm3
    movdqu _TIOSentinelVectors+64(%rip), %xmm4
    movdqu _TIOSentinelVectors+80(%rip), %xmm5
    movdqu _TIOSentinelVectors+96(%rip), %xmm6
    movdqu _TIOSentinelVectors+112(%rip), %xmm7
    movq $8, %rax
    callq *%r12
    addq $8, %rsp
    popq %r12
    popq %rbp
    retq
.globl _TIOSentinelResolver
_TIOSentinelResolver:
    movq %rdi, _TIOSentinelWords(%rip)
    movq %rsi, _TIOSentinelWords+8(%rip)
    movq %rdx, _TIOSentinelWords+16(%rip)
    movq %rcx, _TIOSentinelWords+24(%rip)
    movq %r8, _TIOSentinelWords+32(%rip)
    movq %r9, _TIOSentinelWords+40(%rip)
    movq %rax, _TIOSentinelWords+48(%rip)
    movq %rsp, _TIOSentinelWords+56(%rip)
    movq (%rsp), %r11
    movq %r11, _TIOSentinelWords+64(%rip)
    movdqu %xmm0, _TIOSentinelCaptured(%rip)
    movdqu %xmm1, _TIOSentinelCaptured+16(%rip)
    movdqu %xmm2, _TIOSentinelCaptured+32(%rip)
    movdqu %xmm3, _TIOSentinelCaptured+48(%rip)
    movdqu %xmm4, _TIOSentinelCaptured+64(%rip)
    movdqu %xmm5, _TIOSentinelCaptured+80(%rip)
    movdqu %xmm6, _TIOSentinelCaptured+96(%rip)
    movdqu %xmm7, _TIOSentinelCaptured+112(%rip)
    movq $0x12345678, %rax
    retq
#elif defined(__aarch64__)
.text
.globl _TIOSentinelInvoke
_TIOSentinelInvoke:
    stp x19, x30, [sp, #-16]!
    mov x19, x0
    mov x0, #0x111
    mov x1, #0x222
    mov x2, #0x333
    mov x3, #0x444
    mov x4, #0x555
    mov x5, #0x666
    mov x6, #0x777
    mov x7, #0x888
    mov x8, #0x999
    movi v0.16b, #0x10
    movi v1.16b, #0x11
    movi v2.16b, #0x12
    movi v3.16b, #0x13
    movi v4.16b, #0x14
    movi v5.16b, #0x15
    movi v6.16b, #0x16
    movi v7.16b, #0x17
    blr x19
    ldp x19, x30, [sp], #16
    ret
.globl _TIOSentinelResolver
_TIOSentinelResolver:
    adrp x16, _TIOSentinelWords@PAGE
    add x16, x16, _TIOSentinelWords@PAGEOFF
    stp x0, x1, [x16]
    stp x2, x3, [x16, #16]
    stp x4, x5, [x16, #32]
    stp x6, x7, [x16, #48]
    str x8, [x16, #64]
    mov x17, sp
    str x17, [x16, #72]
    str x30, [x16, #80]
    adrp x16, _TIOSentinelCaptured@PAGE
    add x16, x16, _TIOSentinelCaptured@PAGEOFF
    stp q0, q1, [x16]
    stp q2, q3, [x16, #32]
    stp q4, q5, [x16, #64]
    stp q6, q7, [x16, #96]
    mov x0, #0x5678
    movk x0, #0x1234, lsl #16
    ret
#endif
