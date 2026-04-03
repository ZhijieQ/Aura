import * as Location from 'expo-location';
import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

// 1. 定义你的 HTML 成功运行的样式 URL
const TARGET_STYLE_URL = 'https://osm.zhijie.win/styles/bright.json';
const LOG_PREFIX = '[MapDebug]';
const MADRID_COORDS = { longitude: -3.74922, latitude: 40.463667 };

function getMapLibreModule() {
  try {
    return require('@maplibre/maplibre-react-native');
  } catch (e) {
    return null;
  }
}

const Lib = getMapLibreModule();
const MapLibre = Lib?.default ?? Lib;

// 2. 开启底层日志监控
if (MapLibre?.Logger) {
  MapLibre.Logger.setLogCallback((log) => {
    // 重点监控来自 osm.zhijie.win 的请求
    if (log.message.includes('zhijie.win') || log.tag === 'Mbgl-HttpRequest') {
      console.log(`${LOG_PREFIX} 原生请求日志: ${log.message}`);
    }
  });
}

export default function OpenStreetMapTestScreen() {
  const cameraRef = useRef<any>(null);
  const [currentCoords, setCurrentCoords] = useState<Location.LocationObjectCoords | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);

  useEffect(() => {
    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status === 'granted') {
        const loc = await Location.getCurrentPositionAsync({});
        setCurrentCoords(loc.coords);
      }
    })();
  }, []);

  if (!MapLibre) return <ThemedText>库未加载</ThemedText>;

  return (
    <ThemedView style={styles.container}>
      <View style={styles.mapWrapper}>
        <MapLibre.MapView
          style={styles.map}
          // 3. 关键：直接传入 styleURL，这会像 HTML 一样解析远程 JSON
          styleURL={TARGET_STYLE_URL}
          logoEnabled={false}
          attributionEnabled={true}
          onDidFailLoadingMap={(e: any) => {
            const errorMsg = e?.nativeEvent?.message || '加载失败';
            setMapError(errorMsg);
            console.error(`${LOG_PREFIX} 错误详情:`, e.nativeEvent);
          }}
          onDidFinishLoadingMap={() => {
            setMapLoaded(true);
            setMapError(null);
            console.log(`${LOG_PREFIX} 成功载入目标样式`);
          }}>
          
          <MapLibre.Camera
            ref={cameraRef}
            defaultSettings={{
              centerCoordinate: [MADRID_COORDS.longitude, MADRID_COORDS.latitude],
              zoomLevel: 9, // 与 HTML 保持一致
            }}
          />

          {currentCoords && (
            <MapLibre.ShapeSource
              id="my-location"
              shape={{
                type: 'Feature',
                geometry: {
                  type: 'Point',
                  coordinates: [currentCoords.longitude, currentCoords.latitude],
                },
                properties: {}
              }}>
              <MapLibre.CircleLayer
                id="my-dot"
                style={{
                  circleColor: '#2563eb',
                  circleRadius: 6,
                  circleStrokeWidth: 2,
                  circleStrokeColor: '#fff'
                }}
              />
            </MapLibre.ShapeSource>
          )}
        </MapLibre.MapView>
      </View>

      <ScrollView style={styles.debugPanel}>
        <ThemedText type="defaultSemiBold">域名: osm.zhijie.win</ThemedText>
        <ThemedText style={styles.statusText}>
          状态: {mapLoaded ? '✅ 样式已激活' : '⏳ 正在拉取矢量数据...'}
        </ThemedText>
        
        {mapError && (
          <ThemedText style={styles.errorBox}>
            ❌ 加载异常: {mapError}{'\n'}
            (请检查是否能正常访问该域名)
          </ThemedText>
        )}

        <Pressable 
          onPress={() => cameraRef.current?.setCamera({ 
            centerCoordinate: [MADRID_COORDS.longitude, MADRID_COORDS.latitude], 
            zoomLevel: 13, 
            animationDuration: 1000 
          })} 
          style={styles.button}
        >
          <ThemedText style={styles.btnText}>飞往马德里中心 (查看细节)</ThemedText>
        </Pressable>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingTop: 60, paddingHorizontal: 16 },
  mapWrapper: { flex: 2, borderRadius: 12, overflow: 'hidden', borderWidth: 1, borderColor: '#ddd' },
  map: { flex: 1 },
  debugPanel: { flex: 1, marginTop: 10, padding: 10 },
  statusText: { fontSize: 13, marginVertical: 4 },
  errorBox: { backgroundColor: '#fee2e2', color: '#b91c1c', padding: 8, borderRadius: 4, fontSize: 12, marginTop: 5 },
  button: { backgroundColor: '#10b981', padding: 12, borderRadius: 8, marginTop: 10, alignItems: 'center' },
  btnText: { color: '#fff', fontWeight: 'bold' }
});