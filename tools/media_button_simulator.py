"""
ADB Media Button Simulator - PySide6 Version
Modern GUI to simulate media button presses via ADB
"""
import sys
import os
import subprocess
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QFrame
)
from PySide6.QtCore import Qt, QThread, Signal
from PySide6.QtGui import QFont, QShortcut, QKeySequence

# Find ADB path
def find_adb():
    possible_paths = [
        os.path.join(os.environ.get('LOCALAPPDATA', ''), 'Android', 'Sdk', 'platform-tools', 'adb.exe'),
        os.path.join(os.environ.get('ANDROID_HOME', ''), 'platform-tools', 'adb.exe'),
        os.path.join(os.environ.get('ANDROID_SDK_ROOT', ''), 'platform-tools', 'adb.exe'),
        'adb',
    ]
    for path in possible_paths:
        if path and os.path.exists(path):
            return path
    return 'adb'

ADB_PATH = find_adb()


class AdbWorker(QThread):
    """Background thread for ADB commands"""
    finished = Signal(bool, str)
    
    def __init__(self, command):
        super().__init__()
        self.command = command
    
    def run(self):
        try:
            result = subprocess.run(
                self.command,
                capture_output=True, text=True, timeout=5
            )
            if result.returncode == 0:
                self.finished.emit(True, result.stdout)
            else:
                self.finished.emit(False, result.stderr.strip())
        except FileNotFoundError:
            self.finished.emit(False, "ADB not found!")
        except subprocess.TimeoutExpired:
            self.finished.emit(False, "Timeout - check device")
        except Exception as e:
            self.finished.emit(False, str(e))


class MediaButtonSimulator(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("🎵 ADB Media Button Simulator")
        self.setFixedSize(600, 400)
        self.setStyleSheet(self.get_stylesheet())
        
        # Central widget
        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        layout.setSpacing(20)
        layout.setContentsMargins(40, 30, 40, 30)
        
        # Title
        title = QLabel("Media Button Simulator")
        title.setObjectName("title")
        title.setAlignment(Qt.AlignCenter)
        layout.addWidget(title)
        
        # Top row: PREV | PLAY/PAUSE | NEXT
        top_row = QHBoxLayout()
        top_row.setSpacing(15)
        
        self.btn_prev = QPushButton("⏮  PREV")
        self.btn_prev.setObjectName("mediaBtn")
        self.btn_prev.clicked.connect(lambda: self.send_key("88", "PREVIOUS"))
        top_row.addWidget(self.btn_prev)
        
        self.btn_play = QPushButton("⏯  PLAY/PAUSE")
        self.btn_play.setObjectName("mediaBtn")
        self.btn_play.clicked.connect(lambda: self.send_key("85", "PLAY_PAUSE"))
        top_row.addWidget(self.btn_play)
        
        self.btn_next = QPushButton("NEXT  ⏭")
        self.btn_next.setObjectName("mediaBtn")
        self.btn_next.clicked.connect(lambda: self.send_key("87", "NEXT"))
        top_row.addWidget(self.btn_next)
        
        layout.addLayout(top_row)
        
        # Bottom row: STOP
        bottom_row = QHBoxLayout()
        self.btn_stop = QPushButton("⏹  STOP")
        self.btn_stop.setObjectName("stopBtn")
        self.btn_stop.clicked.connect(lambda: self.send_key("86", "STOP"))
        bottom_row.addStretch()
        bottom_row.addWidget(self.btn_stop)
        bottom_row.addStretch()
        layout.addLayout(bottom_row)
        
        # Separator
        separator = QFrame()
        separator.setFrameShape(QFrame.HLine)
        separator.setObjectName("separator")
        layout.addWidget(separator)
        
        # Status
        self.status = QLabel("Ready. Connect phone via USB with debugging enabled.")
        self.status.setObjectName("status")
        self.status.setAlignment(Qt.AlignCenter)
        self.status.setWordWrap(True)
        layout.addWidget(self.status)
        
        # Check device button
        self.btn_check = QPushButton("🔍 Check Device Connection")
        self.btn_check.setObjectName("checkBtn")
        self.btn_check.clicked.connect(self.check_device)
        layout.addWidget(self.btn_check)
        
        # Shortcut hint
        hint = QLabel("Keyboard: ← Prev | Space Play/Pause | → Next")
        hint.setObjectName("hint")
        hint.setAlignment(Qt.AlignCenter)
        layout.addWidget(hint)
        
        # Keyboard shortcuts
        QShortcut(QKeySequence(Qt.Key_Left), self, lambda: self.send_key("88", "PREVIOUS"))
        QShortcut(QKeySequence(Qt.Key_Right), self, lambda: self.send_key("87", "NEXT"))
        QShortcut(QKeySequence(Qt.Key_Space), self, lambda: self.send_key("85", "PLAY_PAUSE"))
        
        self.worker = None

    def get_stylesheet(self):
        return """
            QMainWindow {
                background-color: #1a1a2e;
            }
            #title {
                font-size: 20px;
                font-weight: bold;
                color: #eee;
                padding: 10px;
            }
            #mediaBtn {
                background-color: #16213e;
                color: white;
                border: 2px solid #0f3460;
                border-radius: 10px;
                padding: 15px 20px;
                font-size: 14px;
                font-weight: bold;
                min-width: 120px;
                min-height: 20px;
            }
            #mediaBtn:hover {
                background-color: #0f3460;
                border-color: #e94560;
            }
            #mediaBtn:pressed {
                background-color: #e94560;
            }
            #stopBtn {
                background-color: #16213e;
                color: #e94560;
                border: 2px solid #e94560;
                border-radius: 10px;
                padding: 12px 30px;
                font-size: 14px;
                font-weight: bold;
            }
            #stopBtn:hover {
                background-color: #e94560;
                color: white;
            }
            #separator {
                color: #0f3460;
            }
            #status {
                color: #aaa;
                font-size: 12px;
                padding: 5px;
            }
            #checkBtn {
                background-color: transparent;
                color: #4ecca3;
                border: 1px solid #4ecca3;
                border-radius: 5px;
                padding: 8px;
                font-size: 12px;
            }
            #checkBtn:hover {
                background-color: #4ecca3;
                color: #1a1a2e;
            }
            #hint {
                color: #666;
                font-size: 10px;
            }
        """

    def send_key(self, keycode, name):
        self.status.setText(f"Sending {name}...")
        self.status.setStyleSheet("color: #4ecca3;")
        
        self.worker = AdbWorker([ADB_PATH, 'shell', 'input', 'keyevent', keycode])
        self.worker.finished.connect(lambda ok, msg: self.on_key_sent(ok, name, msg))
        self.worker.start()

    def on_key_sent(self, success, name, message):
        if success:
            self.status.setText(f"✅ Sent: KEYCODE_MEDIA_{name}")
            self.status.setStyleSheet("color: #4ecca3;")
        else:
            self.status.setText(f"❌ Error: {message}")
            self.status.setStyleSheet("color: #e94560;")

    def check_device(self):
        self.status.setText("Checking device...")
        self.status.setStyleSheet("color: #4ecca3;")
        
        self.worker = AdbWorker([ADB_PATH, 'devices'])
        self.worker.finished.connect(self.on_device_check)
        self.worker.start()

    def on_device_check(self, success, output):
        if success:
            lines = output.strip().split('\n')
            devices = [l for l in lines[1:] if l.strip() and 'device' in l]
            if devices:
                device_id = devices[0].split()[0]
                self.status.setText(f"✅ Found device: {device_id}")
                self.status.setStyleSheet("color: #4ecca3;")
            else:
                self.status.setText("❌ No device found. Enable USB debugging!")
                self.status.setStyleSheet("color: #e94560;")
        else:
            self.status.setText(f"❌ Error: {output}")
            self.status.setStyleSheet("color: #e94560;")


if __name__ == "__main__":
    app = QApplication(sys.argv)
    app.setFont(QFont("Segoe UI", 10))
    window = MediaButtonSimulator()
    window.show()
    sys.exit(app.exec())
