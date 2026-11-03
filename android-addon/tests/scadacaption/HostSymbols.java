package com.turboio.addon;

/**
 * Pure-JVM stand-in for the host ABI resolver so NavGlasses compiles and runs against the
 * test NavReflect double. The real HostSymbols static block probes the vendor classes with
 * Class.forName(..., false, loader) and fails closed off-device, so names here only need to
 * be stable identifiers; NavReflect treats them as opaque keys.
 */
final class HostSymbols {
    // Mirror the legacy host profile so the NavReflect double keeps dispatching on the same
    // member names it already handles ("g" devices, "u" send packet).
    static final String MANAGER_CLASS = "E3.u";
    static final String PACKET_CLASS = "E3.Q";
    static final String BIZ_ENUM_CLASS = "P3.h";
    static final String PRIO_ENUM_CLASS = "E3.b";
    static final String ERROR_ENUM_CLASS = "P3.A";
    static final String SMALL_FILE_CLASS = "Q3.q";
    static final String METHOD_SEND_PACKET = "u";
    static final String METHOD_SEND_FILE = "v";
    static final String METHOD_GET_DEVICES = "g";
    static final String FIELD_ERROR_SEND_FAILED = "r";
    static final String FIELD_SMALL_CORE = "b";
    static final String METHOD_OTA_QUEUE = "o";
    private HostSymbols() {}
}
