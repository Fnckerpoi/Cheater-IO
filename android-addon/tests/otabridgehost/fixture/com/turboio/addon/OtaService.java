package com.turboio.addon; public final class OtaService { static boolean alive=true; static int finishCalls; static boolean alive(){return alive;} static void finish(){finishCalls++;alive=false;} }
