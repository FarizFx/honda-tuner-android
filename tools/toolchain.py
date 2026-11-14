"""Read-only tool discovery shared by the offline test and Android build commands."""
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def find_java_home(explicit=None):
    candidates = []
    if explicit:
        candidates.append(Path(explicit))
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    compiler = shutil.which("javac")
    if compiler:
        candidates.append(Path(compiler).resolve().parent.parent)
    if os.name == "nt":
        for base in (ROOT / "toolchain", Path("D:/Tools/jre25"), Path("D:/Tools"), Path("C:/Program Files/Java"), Path("C:/Program Files/Eclipse Adoptium")):
            if base.is_dir():
                candidates.extend(base.glob("jdk*"))
    suffix = ".exe" if os.name == "nt" else ""
    for candidate in candidates:
        if all((candidate / "bin" / (name + suffix)).is_file() for name in ("java", "javac", "jar", "keytool")):
            return candidate.resolve()
    raise RuntimeError("JDK lengkap belum ditemukan. Gunakan --java-home PATH (JDK 17 atau lebih baru).")


def find_sdk(explicit=None):
    candidates = [Path(explicit)] if explicit else []
    candidates.extend(Path(os.environ[key]) for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT") if os.environ.get(key))
    local = ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                candidates.append(Path(line[8:].replace("\\\\", "\\").replace("\\:", ":")))
    candidates += [ROOT / "toolchain/android-sdk"]
    if os.name == "nt":
        candidates += [Path(os.environ.get("LOCALAPPDATA", "C:/Users/Fariz/AppData/Local")) / "Android/Sdk", Path("C:/Android/Sdk"), Path("D:/Android/Sdk"), Path("D:/Android/sdk"), Path("D:/Tools/android-sdk")]
    for candidate in candidates:
        if (candidate / "platforms/android-35/android.jar").is_file() and (candidate / "build-tools").is_dir():
            return candidate.resolve()
    raise RuntimeError("Android SDK belum ditemukan. Butuh platforms/android-35 dan build-tools 35.0.0. Gunakan --sdk PATH atau buka proyek di Android Studio.")


def java_bin(home, name):
    return str(home / "bin" / (name + (".exe" if os.name == "nt" else "")))
