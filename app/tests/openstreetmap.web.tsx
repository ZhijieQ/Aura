import { Linking, Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

const OSM_WEB_URL = 'https://www.openstreetmap.org';

export default function OpenStreetMapTestWebScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">OpenStreetMap 测试（Web）</ThemedText>
      <ThemedText>
        Web 端不会加载 react-native-maps，避免 Metro 报错。请在 iOS/Android 上测试地图与定位权限逻辑。
      </ThemedText>

      <Pressable
        onPress={() => {
          void Linking.openURL(OSM_WEB_URL);
        }}
        style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}>
        <ThemedText type="defaultSemiBold">在浏览器打开 OpenStreetMap</ThemedText>
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
