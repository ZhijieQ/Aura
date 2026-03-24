import { Link } from 'expo-router';
import { Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

export default function HomeScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <ThemedText type="title">测试入口</ThemedText>
        <ThemedText>用于逐步开发和单项验证，每个测试项对应一个按钮。</ThemedText>
      </ThemedView>

      <ThemedView style={styles.list}>
        <Link href="/tests/openstreetmap" asChild>
          <Pressable style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}>
            <ThemedText type="defaultSemiBold">OpenStreetMap 测试</ThemedText>
            <ThemedText>地图加载 + 定位权限 + 当前位置显示</ThemedText>
          </Pressable>
        </Link>

        <Link href="/tests" asChild>
          <Pressable style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}>
            <ThemedText type="defaultSemiBold">测试中心（模块页）</ThemedText>
            <ThemedText>后续新测试统一放在这个页面管理。</ThemedText>
          </Pressable>
        </Link>
      </ThemedView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 56,
    paddingBottom: 24,
  },
  header: {
    marginBottom: 20,
    gap: 8,
  },
  list: {
    gap: 12,
  },
  button: {
    borderWidth: 1,
    borderRadius: 12,
    borderColor: '#9EA7B3',
    padding: 14,
    gap: 4,
  },
  buttonPressed: {
    opacity: 0.75,
  },
});
