"""Compile isolated host ABI fixtures; no Android runtime or device required."""
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
javac = sys.argv[1] if len(sys.argv) > 1 else "javac"
java = str(pathlib.Path(javac).with_name("java.exe" if javac.endswith(".exe") else "java"))


def put(root, name, body):
    path = root / (name.replace(".", "/") + ".java")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("package " + name.rsplit(".", 1)[0] + ";" + chr(10) + body, encoding="utf-8")


def fixture(root, modern):
    a, b, c, packet, send, file = ("Y3", "j4", "k4", "S", "t", "u") if modern else ("E3", "P3", "Q3", "Q", "u", "v")
    put(root, b + ".h", "public enum h { MARS_FOTA, LAUNCHER, AI_SUBTITLE, TELEPROMPTER }")
    put(root, b + ".A", "public enum A { r }")
    put(root, b + ".Q", "public class Q {}")
    put(root, a + ".b", "public enum b { NORMAL }")
    put(root, c + ".q", "public enum q { b }")
    put(root, c + ".p", "public interface p {}")
    put(root, a + ".C", "public class C { public String a; public boolean b(){ return false; } }")
    put(root, a + ".V", "public class V { public static void o(" + packet + " x){} }")
    put(root, a + "." + packet, "public class " + packet + " { public String b; public " + b + ".h c; public byte[] e; public kotlin.jvm.functions.Function2 k; public " + packet + "(byte[] p,String id,String device," + b + ".h biz," + b + ".Q opt,b priority,boolean one,boolean two," + c + ".p listener,kotlin.jvm.functions.Function2 cb){} }")
    put(root, a + ".u", "public class u implements " + c + ".p { static { if(System.nanoTime()!=0)throw new AssertionError(123); } public static volatile u h; public static java.util.List g(){return null;} public void " + send + "(" + packet + " p,kotlin.jvm.functions.Function2 cb){} public String " + file + "(java.io.File f,String peer," + c + ".q mode,String id){return null;} }")


def fixture120(root):
    base = "com.ffalconxr.mercury.coreconnect"
    put(root, "vb.h", "public enum h { MARS_FOTA, LAUNCHER, AI_SUBTITLE }")
    put(root, "vb.a0", "public enum a0 { MESSAGE_SEND_FAILED }")
    put(root, "vb.r0", "public class r0 {}")
    put(root, "wb.q", "public enum q { SmallCore }")
    put(root, "wb.p", "public interface p {}")
    put(root, base + ".b", "public enum b { NORMAL }")
    put(root, base + ".g0", "public class g0 { public String a; public boolean b(){return false;} }")
    put(root, base + ".y0", "public class y0 { public static void s(v0 packet){} }")
    put(root, base + ".v0", "public class v0 { public String b; public vb.h c; public byte[] e; public kotlin.jvm.functions.Function2 k; public v0(byte[] payload,String id,String device,vb.h biz,vb.r0 option,b priority,boolean first,boolean second,wb.p listener,kotlin.jvm.functions.Function2 callback){} }")
    put(root, base + ".w", "public class w implements wb.p { public static volatile w h; public static java.util.List i(){return null;} public void u(v0 packet,kotlin.jvm.functions.Function2 callback){} public String v(java.io.File file,String peer,wb.q mode,String id){return null;} }")


with tempfile.TemporaryDirectory(prefix="host-symbols-") as temp:
    temp = pathlib.Path(temp)
    cases = [("105", False, False, True), ("107", True, False, True), ("120", "120", False, True),
             ("broken120", "broken120", False, False), ("ambiguous120", "ambiguous120", False, False),
             ("partial107-with105", False, True, False), ("unknown", None, False, False),
             ("ambiguous", "both", False, False), ("partial107", None, True, False),
             ("broken107", True, False, False), ("107-legacy-collision", True, False, True)]
    for label, version, partial, accepted in cases:
        root = temp / label
        root.mkdir()
        put(root, "kotlin.jvm.functions.Function2", "public interface Function2 {}")
        if version is not None:
            if version == "both":
                fixture(root, False)
                fixture(root, True)
            elif version == "ambiguous120":
                fixture120(root)
                fixture(root, False)
            elif version == "broken120":
                fixture120(root)
                put(root, "com.ffalconxr.mercury.coreconnect.g0", "public class g0 { public int a; public boolean b(){return false;} }")
            elif version == "120":
                fixture120(root)
            else:
                fixture(root, version)
        if label == "107-legacy-collision":
            put(root, "E3.b", "public class b {}")
        if partial:
            put(root, "Y3.u", "public class u {}")
        if label == "broken107":
            put(root, "Y3.C", "public class C { public int a; public boolean b(){return false;} }")
        target = root / "com/turboio/addon/HostSymbols.java"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text((ROOT / "src/com/turboio/addon/HostSymbols.java").read_text(encoding="utf-8"), encoding="utf-8")
        expected = "com.ffalconxr.mercury.coreconnect.w" if version == "120" else "Y3.u" if version is True else "E3.u"
        put(root, "com.turboio.addon.Probe", 'public class Probe { public static void main(String[] args){ try { String name=HostSymbols.MANAGER_CLASS; if(!args[0].equals(name))throw new AssertionError(name); if(args[1].equals("reject"))throw new AssertionError("accepted invalid host"); } catch(ExceptionInInitializerError e){ if(!args[1].equals("reject"))throw e; if(!(e.getCause() instanceof IllegalStateException))throw e; } } }')
        out = root / "classes"
        sources = [str(p) for p in root.rglob("*.java")]
        subprocess.run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options", "-d", str(out), *sources], check=True)
        subprocess.run([java, "-cp", str(out), "com.turboio.addon.Probe", expected, "accept" if accepted else "reject"], check=True)
        print(label + " OK", flush=True)

for name in ["NavGlasses", "OfficialOtaBridge", "OtaController", "NativeTransfer", "EntryProbe", "NewsTele"]:
    text = (ROOT / "src/com/turboio/addon" / (name + ".java")).read_text(encoding="utf-8")
    import re
    assert not re.search(r'NavReflect.call\((?:manager|sdkManager),"[uv]",', text), name
    assert "HostSymbols.METHOD_SEND_PACKET" in text, name
print("All six packet senders use mapped methods OK")
