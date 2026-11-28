package com.turboio.addon;

/** Compatibility names for callers that use the profile-validated HostSymbols contract. */
final class HostAbi {
    final String version;

    private HostAbi(String version){this.version=version;}

    static HostAbi get(){
        switch(manager()){
            case "E3.u":return new HostAbi("1.0.5");
            case "Y3.u":return new HostAbi("1.0.7");
            case "com.ffalconxr.mercury.coreconnect.w":return new HostAbi("1.0.20");
            default:throw new IllegalStateException("Unsupported host ABI");
        }
    }

    static String manager(){
        try{return HostSymbols.MANAGER_CLASS;}
        catch(LinkageError e){throw new IllegalStateException("Unsupported host ABI",e);}
    }
    static String message(){manager();return HostSymbols.PACKET_CLASS;}
    static String priority(){manager();return HostSymbols.PRIO_ENUM_CLASS;}
    static String business(){manager();return HostSymbols.BIZ_ENUM_CLASS;}
    static String error(){manager();return HostSymbols.ERROR_ENUM_CLASS;}
    static String core(){manager();return HostSymbols.SMALL_FILE_CLASS;}
    static String send(){manager();return HostSymbols.METHOD_SEND_PACKET;}
    static String file(){manager();return HostSymbols.METHOD_SEND_FILE;}
}
