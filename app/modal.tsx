import { Link } from 'expo-router';
import { Platform, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

export default function ModalScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">This is a modal</ThemedText>
      <Link href="/" dismissTo style={styles.link}>
        <ThemedText type="link">Go to home screen</ThemedText>
      </Link>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  link: {
    marginTop: 15,
    paddingVertical: 15,
  },
  header: {
    // iOS 上高度为 44，Android 上高度为 56
    height: Platform.OS === 'ios' ? 44 : 56, 
    // 或者使用更简洁的 Platform.select
    ...Platform.select({
      ios: { shadowColor: 'black', shadowOpacity: 0.2 },
      android: { elevation: 5 },
    })
  }
});
