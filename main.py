import win32gui


def get_foreground_window_title():
    hwnd = win32gui.GetForegroundWindow()
    return win32gui.GetWindowText(hwnd)


if __name__ == "__main__":
    title = get_foreground_window_title()
    print(f"Foreground window: {title}")
