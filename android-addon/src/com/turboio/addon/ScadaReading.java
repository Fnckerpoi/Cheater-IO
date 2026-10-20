package com.turboio.addon;

/** Immutable scanner reading passed to the RayNeo telemetry card. */
public final class ScadaReading {
    public final String deviceId;
    public final long counter;
    public final int temperatureCentiC;
    public final int pressurePa;
    public final int rssiDbm;
    public final long receivedAtMillis;

    public ScadaReading(String deviceId,long counter,int temperatureCentiC,int pressurePa,int rssiDbm,long receivedAtMillis){
        this.deviceId=deviceId;
        this.counter=counter;
        this.temperatureCentiC=temperatureCentiC;
        this.pressurePa=pressurePa;
        this.rssiDbm=rssiDbm;
        this.receivedAtMillis=receivedAtMillis;
    }
}
