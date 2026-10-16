.private_extern _TIOBridgeRealResolver
#if defined(__x86_64__)
.text
.globl _TIOResolverBridge
.p2align 4
_TIOResolverBridge:
#if defined(TIO_BRIDGE_TESTING)
    movl _TIOBridgeFixtureActive(%rip), %r11d
    testl %r11d, %r11d
    jz Lpass
    cmpq _TIOBridgeFixtureHandle(%rip), %rdi
    jne Lpass
    cmpq _TIOBridgeFixtureName(%rip), %rsi
    jne Lpass
    jmp _TIOBridgeFixtureResolve
Lpass:
#endif
    jmpq *_TIOBridgeRealResolver(%rip)
#elif defined(__aarch64__)
.text
.globl _TIOResolverBridge
.p2align 2
_TIOResolverBridge:
#if defined(TIO_BRIDGE_TESTING)
    adrp x16, _TIOBridgeFixtureActive@PAGE
    add x16, x16, _TIOBridgeFixtureActive@PAGEOFF
    ldar w17, [x16]
    cbz w17, Lpass
    adrp x16, _TIOBridgeFixtureHandle@PAGE
    ldr x17, [x16, _TIOBridgeFixtureHandle@PAGEOFF]
    cmp x0, x17
    b.ne Lpass
    adrp x16, _TIOBridgeFixtureName@PAGE
    ldr x17, [x16, _TIOBridgeFixtureName@PAGEOFF]
    cmp x1, x17
    b.ne Lpass
    b _TIOBridgeFixtureResolve
Lpass:
#endif
    adrp x16, _TIOBridgeRealResolver@PAGE
    add x16, x16, _TIOBridgeRealResolver@PAGEOFF
    ldar x16, [x16]
    br x16
#else
#error Unsupported architecture
#endif
