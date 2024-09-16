import socket
import struct
import io
from PIL import Image
import matplotlib.pyplot as plt

def receive_image():
    # サーバーをポート5001で開始
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(('localhost', 5000))  # Pythonサーバーのホストとポート
        s.listen(1)
        print("Waiting for a connection...")
        conn, addr = s.accept()
        print(f"Connected by {addr}")
        with conn:
            # 画像データのサイズを受け取る
            data_size = struct.unpack('>I', conn.recv(4))[0]  # 4バイトでサイズを受信
            print(f"Receiving image of size: {data_size} bytes")

            # 画像データを受け取る
            image_data = b""
            while len(image_data) < data_size:
                packet = conn.recv(4096)
                if not packet:
                    break
                image_data += packet

            # バイトデータを画像に変換
            image = Image.open(io.BytesIO(image_data))

            # 画像を表示
            plt.imshow(image)
            plt.axis('off')  # 軸を表示しない
            plt.show()

if __name__ == "__main__":
    try:
        receive_image()
    except KeyboardInterrupt:
        print("Server stopped")
