# -*- mode: python ; coding: utf-8 -*-
#
# PyInstaller spec file for main.py
#
# Fix for: ModuleNotFoundError: No module named 'win32gui'
#
# pywin32 modules (win32gui, win32api, win32con, etc.) are not automatically
# detected by PyInstaller because they are loaded via ctypes at runtime.
# Adding them to `hiddenimports` ensures they are bundled into the executable.

block_cipher = None

a = Analysis(
    ['main.py'],
    pathex=[],
    binaries=[],
    datas=[],
    hiddenimports=[
        'win32gui',
        'win32api',
        'win32con',
        'win32com',
        'win32com.client',
        'pywintypes',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='main',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
