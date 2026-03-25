import { Linking, Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

const VECTOR_WEB_URL = 'http://localhost:8080/spain?token=my_secret_key';

export default function OpenStreetMapTestWebScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">OpenStreetMap 测试（Web）</ThemedText>
      <ThemedText>
        当前默认打开本地矢量服务（spain）。如需切换中国数据，把 URL 中的 spain 改为 china 即可。
      </ThemedText>

      <Pressable
        onPress={() => {
          void Linking.openURL(VECTOR_WEB_URL);
        }}
        style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}>
        <ThemedText type="defaultSemiBold">在浏览器打开本地矢量地图（spain）</ThemedText>
      </Pressable>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 16,
    paddingHorizontal: 16,
    paddingBottom: 24,
    gap: 12,
  },
  button: {
    borderWidth: 1,
    borderRadius: 10,
    borderColor: '#9EA7B3',
    paddingVertical: 12,
    paddingHorizontal: 14,
    alignItems: 'center',
  },
  buttonPressed: {
    opacity: 0.8,
  },
});
