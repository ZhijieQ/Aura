import { Link } from 'expo-router';
import { Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

const TEST_ITEMS = [
  {
    key: 'openstreetmap',
    title: 'OpenStreetMap 测试',
    desc: '验证地图渲染、定位权限弹窗、当前位置标记显示。',
    href: '/tests/openstreetmap' as const,
  },
];

export default function TestHubScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">测试中心</ThemedText>
      <ThemedText style={styles.subTitle}>每新增一个测试，就在这里新增一个按钮。</ThemedText>

      <ThemedView style={styles.list}>
        {TEST_ITEMS.map((item) => (
          <Link key={item.key} href={item.href} asChild>
            <Pressable style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}>
              <ThemedText type="defaultSemiBold">{item.title}</ThemedText>
              <ThemedText>{item.desc}</ThemedText>
            </Pressable>
          </Link>
        ))}
      </ThemedView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 24,
    paddingBottom: 24,
    gap: 10,
  },
  subTitle: {
    marginBottom: 10,
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
