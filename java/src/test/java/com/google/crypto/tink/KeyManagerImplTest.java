// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.crypto.tink.proto.AesGcmKey;
import com.google.crypto.tink.proto.AesGcmKeyFormat;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.subtle.AesGcmJce;
import com.google.crypto.tink.subtle.Random;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.security.GeneralSecurityException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests the methods implemented in KeyManagerImpl using the concrete implementation above. */
@RunWith(JUnit4.class)
public final class KeyManagerImplTest {
  /** Implementation of an InternalKeyManager for testing. */
  private static class TestInternalKeyManager extends InternalKeyManager<AesGcmKey> {
    public TestInternalKeyManager() {
      super(
          AesGcmKey.class,
          new PrimitiveFactory<Aead, AesGcmKey>(Aead.class) {
            @Override
            public Aead getPrimitive(AesGcmKey key) throws GeneralSecurityException {
              return new AesGcmJce(key.getKeyValue().toByteArray());
            }
          },
          new PrimitiveFactory<FakeAead, AesGcmKey>(FakeAead.class) {
            @Override
            public FakeAead getPrimitive(AesGcmKey key) {
              return new FakeAead();
            }
          });
    }

    @Override
    public String getKeyType() {
      return "type.googleapis.com/google.crypto.tink.AesGcmKey";
    }

    @Override
    public int getVersion() {
      return 1;
    }

    @Override
    public KeyMaterialType keyMaterialType() {
      return KeyMaterialType.SYMMETRIC;
    }

    @Override
    public void validateKey(AesGcmKey keyProto) throws GeneralSecurityException {
      // Throw by hand so we can verify the exception comes from here.
      if (keyProto.getKeyValue().size() != 16) {
        throw new GeneralSecurityException("validateKey(AesGcmKey) failed");
      }
    }

    @Override
    public AesGcmKey parseKey(ByteString byteString) throws InvalidProtocolBufferException {
      return AesGcmKey.parseFrom(byteString);
    }

    @Override
    public KeyFactory<AesGcmKeyFormat, AesGcmKey> keyFactory() {
      return new KeyFactory<AesGcmKeyFormat, AesGcmKey>(AesGcmKeyFormat.class) {
        @Override
        public void validateKeyFormat(AesGcmKeyFormat format) throws GeneralSecurityException {
          // Throw by hand so we can verify the exception comes from here.
          if (format.getKeySize() != 16) {
            throw new GeneralSecurityException("validateKeyFormat(AesGcmKeyFormat) failed");
          }
        }

        @Override
        public AesGcmKeyFormat parseKeyFormat(ByteString byteString)
            throws InvalidProtocolBufferException {
          return AesGcmKeyFormat.parseFrom(byteString);
        }

        @Override
        public AesGcmKey createKey(AesGcmKeyFormat format) throws GeneralSecurityException {
          return AesGcmKey.newBuilder()
              .setKeyValue(ByteString.copyFrom(Random.randBytes(format.getKeySize())))
              .setVersion(getVersion())
              .build();
        }
      };
    }
  }

  @Test
  public void getPrimitive_ByteString_works() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    MessageLite key = keyManager.newKey(AesGcmKeyFormat.newBuilder().setKeySize(16).build());
    keyManager.getPrimitive(key.toByteString());
  }

  @Test
  public void getPrimitive_FakeAead_ByteString_works() throws Exception {
    KeyManager<FakeAead> fakeAeadKeyManager =
        new KeyManagerImpl<>(new TestInternalKeyManager(), FakeAead.class);
    MessageLite key =
        fakeAeadKeyManager.newKey(AesGcmKeyFormat.newBuilder().setKeySize(16).build());
    fakeAeadKeyManager.getPrimitive(key.toByteString());
  }

  @Test
  public void creatingKeyManager_nonSupportedPrimitive_fails() throws Exception {
    try {
      new KeyManagerImpl<>(new TestInternalKeyManager(), Integer.class);
      fail("IllegalArgumentException expected.");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void getPrimitive_ByteString_throwsInvalidKey() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    MessageLite notAKey = AesGcmKey.getDefaultInstance();
    try {
      keyManager.getPrimitive(notAKey.toByteString());
      fail("expected GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertThat(e.toString()).contains("validateKey(AesGcmKey) failed");
    }
  }

  @Test
  public void getPrimitive_MessageLite_works() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    MessageLite key = keyManager.newKey(AesGcmKeyFormat.newBuilder().setKeySize(16).build());
    keyManager.getPrimitive(key);
  }

  @Test
  public void getPrimitive_MessageLite_throwsIfVoid() throws Exception {
    KeyManager<Void> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Void.class);
    MessageLite key = keyManager.newKey(AesGcmKeyFormat.newBuilder().setKeySize(16).build());
    try {
      keyManager.getPrimitive(key);
      fail("expected GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertThat(e.toString()).contains("Void");
    }
  }

  @Test
  public void getPrimitive_MessageLite_throwsWrongProto() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    MessageLite notAKey = AesGcmKeyFormat.getDefaultInstance();
    try {
      keyManager.getPrimitive(notAKey);
      fail("expected GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertThat(e.toString()).contains("Expected proto of type");
    }
  }

  @Test
  public void getPrimitive_MessageLite_throwsInvalidKey() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    MessageLite notAKey = AesGcmKey.getDefaultInstance();
    try {
      keyManager.getPrimitive(notAKey);
      fail("expected GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertThat(e.toString()).contains("validateKey(AesGcmKey) failed");
    }
  }

  @Test
  public void newKey_ByteString_works() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    keyManager.newKey(AesGcmKeyFormat.newBuilder().setKeySize(16).build().toByteString());
  }

  @Test
  public void newKey_ByteString_throwsInvalidKeySize() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    try {
      keyManager.newKey(AesGcmKeyFormat.newBuilder().setKeySize(17).build().toByteString());
      fail("expected GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertThat(e.toString()).contains("validateKeyFormat(AesGcmKeyFormat) failed");
    }
  }

  @Test
  public void newKey_MessageLite_works() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    keyManager.newKey(AesGcmKeyFormat.newBuilder().setKeySize(16).build());
  }

  @Test
  public void newKey_MessageLite_throwsWrongProto() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    try {
      // Note: newKey expects AesGcmKeyFormat, not AesGcmKey.
      keyManager.newKey(AesGcmKey.getDefaultInstance());
      fail("expected GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertThat(e.toString()).contains("Expected proto of type");
    }
  }

  @Test
  public void newKey_MessageLite_throwsInvalidKeySize() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    try {
      // Note: newKey expects AesGcmKeyFormat, not AesGcmKey.
      keyManager.newKey((MessageLite) AesGcmKeyFormat.getDefaultInstance());
      fail("expected GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertThat(e.toString()).contains("validateKeyFormat(AesGcmKeyFormat) failed");
    }
  }

  @Test
  public void doesSupport_returnsTrue() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    assertThat(keyManager.doesSupport("type.googleapis.com/google.crypto.tink.AesGcmKey")).isTrue();
  }

  @Test
  public void doesSupport_returnsFalse() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    assertThat(keyManager.doesSupport("type.googleapis.com/SomeOtherKey")).isFalse();
  }

  @Test
  public void getKeyType() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    assertThat(keyManager.getKeyType())
        .isEqualTo("type.googleapis.com/google.crypto.tink.AesGcmKey");
  }

  @Test
  public void newKeyData_works() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    keyManager.newKeyData(AesGcmKeyFormat.newBuilder().setKeySize(16).build().toByteString());
  }

  @Test
  public void newKeyData_typeUrlCorrect() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    assertThat(
            keyManager
                .newKeyData(AesGcmKeyFormat.newBuilder().setKeySize(16).build().toByteString())
                .getTypeUrl())
        .isEqualTo("type.googleapis.com/google.crypto.tink.AesGcmKey");
  }

  @Test
  public void newKeyData_valueLengthCorrect() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    // We allow the keysize to be bigger than 16 since proto serialized adds some overhead.
    assertThat(
            keyManager
                .newKeyData(AesGcmKeyFormat.newBuilder().setKeySize(16).build().toByteString())
                .getValue()
                .size())
        .isAtLeast(16);
  }

  @Test
  public void newKeyData_wrongKeySize_throws() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    // We allow the keysize to be bigger than 16 since proto serialized adds some overhead.
    try {
      keyManager.newKeyData(AesGcmKeyFormat.newBuilder().setKeySize(17).build().toByteString());
      fail("expected GeneralSecurityException");
    } catch (GeneralSecurityException e) {
      assertThat(e.toString()).contains("validateKeyFormat(AesGcmKeyFormat) failed");
    }
  }

  @Test
  public void newKeyData_keyMaterialTypeCorrect() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    assertThat(
            keyManager
                .newKeyData(AesGcmKeyFormat.newBuilder().setKeySize(16).build().toByteString())
                .getKeyMaterialType())
        .isEqualTo(KeyMaterialType.SYMMETRIC);
  }

  @Test
  public void getPrimitiveClass() throws Exception {
    KeyManager<Aead> keyManager = new KeyManagerImpl<>(new TestInternalKeyManager(), Aead.class);
    assertThat(keyManager.getPrimitiveClass()).isEqualTo(Aead.class);
  }

  /** Implementation of an InternalKeyManager for testing, not supporting creating new keys. */
  private static class TestInternalKeyManagerWithoutKeyFactory
      extends InternalKeyManager<AesGcmKey> {
    public TestInternalKeyManagerWithoutKeyFactory() {
      super(AesGcmKey.class);
    }

    @Override
    public String getKeyType() {
      return "type.googleapis.com/google.crypto.tink.AesGcmKey";
    }

    @Override
    public int getVersion() {
      return 1;
    }

    @Override
    public KeyMaterialType keyMaterialType() {
      return KeyMaterialType.SYMMETRIC;
    }

    @Override
    public void validateKey(AesGcmKey keyProto) {}

    @Override
    public AesGcmKey parseKey(ByteString byteString) throws InvalidProtocolBufferException {
      return AesGcmKey.parseFrom(byteString);
    }
  }

  @Test
  public void newKey_ByteString_throwsUnsupportedOperation() throws Exception {
    KeyManager<Void> keyManager =
        new KeyManagerImpl<>(new TestInternalKeyManagerWithoutKeyFactory(), Void.class);
    try {
      keyManager.newKey(ByteString.copyFromUtf8(""));
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException e) {
      // expected
    }
  }

  @Test
  public void newKey_byteString_throwsUnsupportedOperation() throws Exception {
    KeyManager<Void> keyManager =
        new KeyManagerImpl<>(new TestInternalKeyManagerWithoutKeyFactory(), Void.class);
    try {
      keyManager.newKey(ByteString.copyFromUtf8(""));
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException e) {
      // expected
    }
  }

  @Test
  public void newKey_messageList_throwsUnsupportedOperation() throws Exception {
    KeyManager<Void> keyManager =
        new KeyManagerImpl<>(new TestInternalKeyManagerWithoutKeyFactory(), Void.class);
    try {
      keyManager.newKey(AesGcmKey.getDefaultInstance());
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException e) {
      // expected
    }
  }

  @Test
  public void newKeyData_byteString_throwsUnsupportedOperation() throws Exception {
    KeyManager<Void> keyManager =
        new KeyManagerImpl<>(new TestInternalKeyManagerWithoutKeyFactory(), Void.class);
    try {
      keyManager.newKeyData(ByteString.copyFromUtf8(""));
      fail("UnsupportedOperationException expected");
    } catch (UnsupportedOperationException e) {
      // expected
    }
  }

  private static class FakeAead {}
}
