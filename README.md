# hello-world

I don't know what to say. It's just for something new.

---

## PyInstaller – `ModuleNotFoundError: No module named win32gui` 해결 방법

PyInstaller로 빌드할 때 `win32gui` (pywin32) 모듈을 찾지 못하는 문제가 발생하는 경우, 아래 방법으로 해결할 수 있습니다.

### 원인

`win32gui`는 `pywin32` 패키지에 포함된 모듈로, 런타임에 ctypes를 통해 동적으로 로드됩니다.  
PyInstaller의 정적 분석은 이러한 동적 임포트를 자동으로 감지하지 못해 빌드된 실행 파일에 모듈이 누락됩니다.

### 해결 방법 1: `--hidden-import` 옵션 사용

```bash
pyinstaller --hidden-import win32gui --hidden-import win32api --hidden-import win32con your_script.py
```

### 해결 방법 2: `.spec` 파일에 `hiddenimports` 추가 (권장)

이 저장소의 `main.spec` 파일을 참고하세요. `hiddenimports` 목록에 필요한 모듈을 추가합니다.

```python
a = Analysis(
    ['main.py'],
    ...
    hiddenimports=[
        'win32gui',
        'win32api',
        'win32con',
        'win32com',
        'win32com.client',
        'pywintypes',
    ],
    ...
)
```

그 후 spec 파일로 빌드:

```bash
pyinstaller main.spec
```

### 필요 패키지 설치

```bash
pip install -r requirements.txt
```

또는 직접 설치:

```bash
pip install pywin32 pyinstaller
```

> **참고:** `pywin32`를 설치한 후에는 아래 스크립트를 실행해 pywin32를 시스템에 등록해야 할 수도 있습니다.
>
> ```bash
> python Scripts/pywin32_postinstall.py -install
> ```
