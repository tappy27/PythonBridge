# recieve_image_example.py
import socket
import logging
import struct
import threading
import os

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(levelname)s] %(message)s')

def recv_all(sock, length):
    data = b''
    while len(data) < length:
        packet = sock.recv(length - len(data))
        if not packet:
            return None
        data += packet
    return data

def handle_client(conn, addr, image_count_lock, image_count, save_frequency=100):
    logging.info(f"Connected by {addr}")
    try:
        while True:
            size_data = recv_all(conn, 4)
            if not size_data:
                logging.info(f"Connection closed by {addr}")
                break

            # 画像サイズをデコード
            image_size = struct.unpack('>I', size_data)[0]
            logging.debug(f"Expecting image of size: {image_size} bytes from {addr}")

            # 画像データを受信
            image_data = recv_all(conn, image_size)
            if not image_data:
                logging.warning(f"Failed to receive image data from {addr}")
                break

            logging.debug(f"Received image of size: {len(image_data)} bytes from {addr}")

            # 画像カウントの更新
            with image_count_lock:
                image_count[0] += 1
                current_count = image_count[0]

            # 指定された頻度で画像を保存
            if current_count % save_frequency == 0:
                filename = f'received_image_{current_count}.png'
                try:
                    with open(filename, 'wb') as f:
                        f.write(image_data)
                    logging.info(f"Image saved as {filename} from {addr}")
                except Exception as e:
                    logging.error(f"Failed to save image {filename}: {e}")
    except Exception as e:
        logging.error(f"An error occurred with {addr}: {e}")
    finally:
        conn.close()
        logging.info(f"Connection closed with {addr}")

def start_simple_server(host='localhost', port=5000, save_frequency=100):
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        server_socket.bind((host, port))
        logging.debug(f"Binding to {host}:{port} successful.")
    except Exception as e:
        logging.error(f"Failed to bind to {host}:{port} - {e}")
        return

    server_socket.listen()
    logging.info(f"Simple server listening on {host}:{port}")

    image_count = [0]
    image_count_lock = threading.Lock()

    try:
        while True:
            logging.debug("Waiting for a new connection...")
            conn, addr = server_socket.accept()
            client_thread = threading.Thread(target=handle_client, args=(conn, addr, image_count_lock, image_count, save_frequency))
            client_thread.daemon = True
            client_thread.start()
    except KeyboardInterrupt:
        logging.info("Server shutting down due to KeyboardInterrupt.")
    except Exception as e:
        logging.error(f"An unexpected error occurred: {e}")
    finally:
        server_socket.close()
        logging.debug("Server socket closed.")

if __name__ == "__main__":
    start_simple_server(save_frequency=100)
