package debugger;

import haxe.root.Std;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings(value = {"rawtypes", "unchecked"})
public class DapHaxeProtocol extends haxe.lang.HxObject {
  public interface CommandCallback<PT, RT> {
    void onResponse(DapHaxeMessage<PT, RT> message);
  }

  private final static int BUFFER_SIZE = 4096;
  private final static int INVALID_MESSAGE_LENGTH = -1;
  private final static int LENGTH_BYTES = 4;

  public static ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN);
  public static int nextMessageLength = INVALID_MESSAGE_LENGTH;

  private final static byte[] READ_MESSAGE_DATA_CACHE = new byte[BUFFER_SIZE];

  public DapHaxeProtocol(haxe.lang.EmptyObject empty) {
  }
  
  public static void appendBuffer(byte[] data, int length) {
    ensureCapacity(length);
    buffer.put(data, 0, length);
  }

  public static void clearBuffer() {
    buffer.clear();
    nextMessageLength = INVALID_MESSAGE_LENGTH;
  }

  private static void ensureCapacity(int additionalDataLength) {
    if (buffer.remaining() < additionalDataLength) {
      int newSize = (buffer.capacity() + additionalDataLength) * 2;
      ByteBuffer newBuffer = ByteBuffer.allocate(newSize).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      buffer.flip();
      newBuffer.put(buffer);
      buffer = newBuffer;
    }
  }

  private static DapHaxeMessage processData() {
    if (nextMessageLength == INVALID_MESSAGE_LENGTH) {
      if (buffer.position() < LENGTH_BYTES) {
        return null;
      }
      else {
        buffer.flip();
        nextMessageLength = buffer.getInt();
        buffer.compact();
      }
    }

    if (buffer.position() < nextMessageLength) {
      return null;
    }

    buffer.flip();
    byte[] messageData = new byte[nextMessageLength];
    buffer.get(messageData, 0, nextMessageLength);
    buffer.compact();

    nextMessageLength = INVALID_MESSAGE_LENGTH;
    try {
      return DapHaxeMessage.fromBytes(messageData);
    }
    catch (Throwable _g) {
      Object e = ((haxe.Exception.caught(_g).unwrap()));
      throw new RuntimeException("Expected Message, but got " + ": " + Std.string(e));
    }
  }

  public static DapHaxeMessage readMessage(InputStream is) {
    try {
      int bytesRead;
      DapHaxeMessage message;
      if ((message = processData()) != null) {
        return message;
      }

      while ((bytesRead = is.read(READ_MESSAGE_DATA_CACHE)) != -1) {
        appendBuffer(READ_MESSAGE_DATA_CACHE, bytesRead);
        if ((message = processData()) != null) {
          return message;
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    return null;
  }

  public static void writeCommand(OutputStream output, DapHaxeMessage command) {
    var body = command.toBytes();
    ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
    header.putInt(body.length);
    System.out.println("Writing command: \n" + new String(header.array()) + new String(body));

    try {
      output.write(header.array());
      output.write(body);
      output.flush();
    }
    catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}

