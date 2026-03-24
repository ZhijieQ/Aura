import * as Location from 'expo-location';
import { useEffect, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

const DEFAULT_REGION = {
  latitude: 39.9042,
  longitude: 116.4074,
  latitudeDelta: 0.15,
  longitudeDelta: 0.15,
};

type PermissionState = 'unknown' | 'granted' | 'denied';

function getMapModule() {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    return require('react-native-maps') as typeof import('react-native-maps');
  } catch {
    return null;
  }
}

export default function OpenStreetMapTestScreen() {
  const mapRef = useRef<any>(null);
  const [permissionState, setPermissionState] = useState<PermissionState>('unknown');
  const [currentCoords, setCurrentCoords] = useState<Location.LocationObjectCoords | null>(null);
  const [requesting, setRequesting] = useState(false);
  const mapModule = getMapModule();

  const requestPermissionAndLocate = async () => {
    setRequesting(true);

    try {
      const permission = await Location.requestForegroundPermissionsAsync();

      if (permission.status !== 'granted') {
        setPermissionState('denied');
        setCurrentCoords(null);
        return;
      }

      setPermissionState('granted');
      const current = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });

      setCurrentCoords(current.coords);
      mapRef.current?.animateToRegion(
        {
          latitude: current.coords.latitude,
          longitude: current.coords.longitude,
          latitudeDelta: 0.02,
          longitudeDelta: 0.02,
        },
        400
      );
    } finally {
      setRequesting(false);
    }
  };

  useEffect(() => {
    void requestPermissionAndLocate();
  }, []);

  if (Platform.OS === 'web') {
    return (
      <ThemedView style={styles.container}>
        <ThemedText type="title">OpenStreetMap 测试（Web）</ThemedText>
        <ThemedText>请访问 /tests/openstreetmap 路由的 web 版本页面进行测试。</ThemedText>
      </ThemedView>
    );
  }

  if (!mapModule) {
    return (
      <ThemedView style={styles.container}>
        <ThemedText type="title">OpenStreetMap 测试</ThemedText>
        <ThemedText>
          当前运行环境未加载到 react-native-maps 原生模块（RNMapsAirModule）。请使用 Development Build，或重新安装支持地图模块的客户端后重试。
        </ThemedText>
      </ThemedView>
    );
  }

  const MapView = mapModule.default;
  const Marker = mapModule.Marker;
  const UrlTile = mapModule.UrlTile;

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <ThemedText type="title">OpenStreetMap 测试</ThemedText>
        <ThemedText>
          当前权限：
          {permissionState === 'granted' ? '已授权' : permissionState === 'denied' ? '已拒绝' : '待确认'}
        </ThemedText>
      </ThemedView>

      <View style={styles.mapWrapper}>
        <MapView
          ref={mapRef}
          style={styles.map}
          initialRegion={DEFAULT_REGION}
          showsMyLocationButton
          showsUserLocation={permissionState === 'granted'}>
          <UrlTile
            zIndex={-1}
            maximumZ={19}
            shouldReplaceMapContent
            urlTemplate="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          {currentCoords ? (
            <Marker
              coordinate={{
                latitude: currentCoords.latitude,
                longitude: currentCoords.longitude,
              }}
              title="当前位置"
              description="定位成功"
            />
          ) : null}
        </MapView>
      </View>

      <Pressable
        onPress={requestPermissionAndLocate}
        style={({ pressed }) => [styles.button, pressed && styles.buttonPressed, requesting && styles.disabled]}
        disabled={requesting}>
        <ThemedText type="defaultSemiBold">
          {requesting ? '处理中...' : '重新请求定位并刷新当前位置'}
        </ThemedText>
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
  header: {
    gap: 6,
  },
  mapWrapper: {
    flex: 1,
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#9EA7B3',
  },
  map: {
    flex: 1,
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
  disabled: {
    opacity: 0.6,
  },
});
