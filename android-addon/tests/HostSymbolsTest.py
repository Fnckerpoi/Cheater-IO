"""Compile isolated host ABI fixtures; no Android runtime or device required."""
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
javac = sys.argv[1] if len(sys.argv) > 1 else "javac"
java = str(pathlib.Path(javac).with_name("java.exe" if javac.endswith(".exe") else "java"))


def put(root, name, body):
    path = root / (name.replace(".", "/") + ".java")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("package " + name.rsplit(".", 1)[0] + ";" + chr(10) + body, encoding="utf-8")


def write_fixture(root_mgr, root_pkt, version):
    if version == "110":
        a, b, c, packet, send, file, dev_m, err_f, small_f, ota_m, mgr_cls, dev_cls, ota_cls = (
            "O4", "a5", "b5", "V", "t", "u", "i", "MESSAGE_SEND_FAILED", "SmallCore", "s", "v", "G", "Y"
        )
    elif version == "107" or version is True:
        a, b, c, packet, send, file, dev_m, err_f, small_f, ota_m, mgr_cls, dev_cls, ota_cls = (
            "Y3", "j4", "k4", "S", "t", "u", "g", "r", "b", "o", "u", "C", "V"
        )
    else:
        a, b, c, packet, send, file, dev_m, err_f, small_f, ota_m, mgr_cls, dev_cls, ota_cls = (
            "E3", "P3", "Q3", "Q", "u", "v", "g", "r", "b", "o", "u", "C", "V"
        )
    # Common / manager files go to root_mgr
    put(root_mgr, b + ".h", "public enum h { MARS_FOTA, LAUNCHER, AI_SUBTITLE, TELEPROMPTER }")
    put(root_mgr, b + ".A", "public enum A { " + err_f + " }")
    put(root_mgr, b + ".Q", "public class Q {}")
    prio_cls = "c" if version == "110" else "b"
    put(root_mgr, a + "." + prio_cls, "public enum " + prio_cls + " { NORMAL }")
    put(root_mgr, c + ".q", "public enum q { " + small_f + " }")
    put(root_mgr, c + ".p", "public interface p {}")
    put(root_mgr, a + "." + dev_cls, "public class " + dev_cls + " { public String a; public boolean b(){ return false; } }")
    put(root_mgr, a + "." + ota_cls, "public class " + ota_cls + " { public static void " + ota_m + "(" + a + "." + packet + " x){} }")
    # packet goes to root_pkt to avoid Windows case-collision between v.java and V.java
    put(root_pkt, a + "." + packet, "public class " + packet + " { public String b; public " + b + ".h c; public byte[] e; public kotlin.jvm.functions.Function2 k; public " + packet + "(byte[] p,String id,String device," + b + ".h biz," + b + ".Q opt," + a + "." + prio_cls + " priority,boolean one,boolean two," + c + ".p listener,kotlin.jvm.functions.Function2 cb){} }")
    put(root_mgr, a + "." + mgr_cls, "public class " + mgr_cls + " implements " + c + ".p { static { if(System.nanoTime()!=0)throw new AssertionError(123); } public static volatile " + a + "." + mgr_cls + " h; public static java.util.List " + dev_m + "(){return null;} public void " + send + "(" + a + "." + packet + " p,kotlin.jvm.functions.Function2 cb){} public String " + file + "(java.io.File f,String peer," + c + ".q mode,String id){return null;} }")


with tempfile.TemporaryDirectory(prefix="host-symbols-") as temp:
    temp = pathlib.Path(temp)
    cases = [
        ("105", False, False, True),
        ("107", True, False, True),
        ("110", "110", False, True),
        ("partial107-with105", False, True, False),
        ("unknown", None, False, False),
        ("ambiguous", "both", False, False),
        ("partial107", None, True, False),
        ("broken107", True, False, False),
        ("107-legacy-collision", True, False, True),
        ("broken110", "broken110", False, False),
        ("ambiguous110", "ambiguous110", False, False)
    ]
    for label, version, partial, accepted in cases:
        case_dir = temp / label
        root_mgr = case_dir / "src_mgr"
        root_pkt = case_dir / "src_pkt"
        root_probe = case_dir / "src_probe"
        out_mgr = case_dir / "classes_mgr"
        out_pkt = case_dir / "classes_pkt"
        out_probe = case_dir / "classes_probe"
        for d in [root_mgr, root_pkt, root_probe, out_mgr, out_pkt, out_probe]:
            d.mkdir(parents=True, exist_ok=True)

        put(root_mgr, "kotlin.jvm.functions.Function2", "public interface Function2 {}")
        put(root_pkt, "kotlin.jvm.functions.Function2", "public interface Function2 {}")

        if version is not None:
            if version == "both":
                write_fixture(root_mgr, root_pkt, False)
                write_fixture(root_mgr, root_pkt, True)
            elif version == "ambiguous110":
                write_fixture(root_mgr, root_pkt, "110")
                write_fixture(root_mgr, root_pkt, True)
            elif version == "broken110":
                write_fixture(root_mgr, root_pkt, "110")
                # corrupt device class for 110
                put(root_mgr, "O4.G", "public class G { public int a; public boolean b(){return false;} }")
            else:
                write_fixture(root_mgr, root_pkt, version)

        if label == "107-legacy-collision":
            put(root_mgr, "E3.b", "public class b {}")
        if partial:
            put(root_mgr, "Y3.u", "public class u {}")
        if label == "broken107":
            put(root_mgr, "Y3.C", "public class C { public int a; public boolean b(){return false;} }")

        # Compile pkt first
        pkt_sources = [str(p) for p in root_pkt.rglob("*.java")]
        if pkt_sources:
            subprocess.run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options", "-cp", str(root_mgr), "-d", str(out_pkt), *pkt_sources], check=True)

        # Compile mgr
        mgr_sources = [str(p) for p in root_mgr.rglob("*.java")]
        if mgr_sources:
            subprocess.run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options", "-cp", str(out_pkt), "-d", str(out_mgr), *mgr_sources], check=True)

        # Package into fixture.jar so case-sensitivity is preserved on Windows
        jar_path = case_dir / "fixture.jar"
        seen = set()
        with zipfile.ZipFile(jar_path, "w") as z:
            for cfile in out_mgr.rglob("*.class"):
                rel = cfile.relative_to(out_mgr).as_posix()
                if rel not in seen:
                    z.write(cfile, rel)
                    seen.add(rel)
            for cfile in out_pkt.rglob("*.class"):
                rel = cfile.relative_to(out_pkt).as_posix()
                if rel not in seen:
                    z.write(cfile, rel)
                    seen.add(rel)

        # Now compile HostSymbols and Probe against fixture.jar
        target = root_probe / "com/turboio/addon/HostSymbols.java"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text((ROOT / "src/com/turboio/addon/HostSymbols.java").read_text(encoding="utf-8"), encoding="utf-8")
        if version == "110":
            expected = "O4.v"
        elif version is True or version == "107":
            expected = "Y3.u"
        else:
            expected = "E3.u"
        put(root_probe, "com.turboio.addon.Probe", 'public class Probe { public static void main(String[] args){ try { String name=HostSymbols.MANAGER_CLASS; if(!args[0].equals(name))throw new AssertionError(name); if(args[1].equals("reject"))throw new AssertionError("accepted invalid host"); } catch(ExceptionInInitializerError e){ if(!args[1].equals("reject"))throw e; if(!(e.getCause() instanceof IllegalStateException))throw e; } } }')
        probe_sources = [str(p) for p in root_probe.rglob("*.java")]
        subprocess.run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options", "-cp", str(jar_path), "-d", str(out_probe), *probe_sources], check=True)

        # Run probe
        subprocess.run([java, "-cp", str(out_probe) + ";" + str(jar_path), "com.turboio.addon.Probe", expected, "accept" if accepted else "reject"], check=True)
        print(label + " OK", flush=True)

for name in ["NavGlasses", "OfficialOtaBridge", "OtaController", "NativeTransfer", "EntryProbe", "NewsTele"]:
    text = (ROOT / "src/com/turboio/addon" / (name + ".java")).read_text(encoding="utf-8")
    import re
    assert not re.search(r'NavReflect\.call\((?:manager|sdkManager),"[uv]",', text), name
    assert "HostSymbols.METHOD_SEND_PACKET" in text, name
print("All six packet senders use mapped methods OK")
