import * as Location from 'expo-location';
import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

// ================= 调试工具 =================
const LOG_PREFIX = '[MapDebug]';
function log(...args: any[]) {
  console.log(LOG_PREFIX, new Date().toISOString().slice(11, 23), ...args);
}
function error(...args: any[]) {
  console.error(LOG_PREFIX, ...args);
}

// ================= 1. 加载 MapLibre 库 =================
let MapLibreModule: any = null;
try {
  const Lib = require('@maplibre/maplibre-react-native');
  MapLibreModule = Lib?.default ?? Lib;
  log('✅ MapLibre 模块加载成功', { hasDefault: !!Lib?.default, keys: Object.keys(MapLibreModule || {}).slice(0, 5) });

  // 🌟【核心配置】为原生引擎全局注入请求头，防止瓦片和字体下载被 WAF/CDN 拦截 🌟
  if (MapLibreModule && typeof MapLibreModule.addCustomHeader === 'function') {
    const userAgent = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (Chrome/120.0.0.0) Mobile Safari/537.36';
    MapLibreModule.addCustomHeader('User-Agent', userAgent);
    log('✅ 已向原生引擎注入全局请求头 (User-Agent)');
  }
} catch (e: any) {
  error('❌ 加载 @maplibre/maplibre-react-native 失败', e.message);
}

// 开启原生网络日志（拦截样式相关的 HTTP 请求）
if (MapLibreModule?.Logger) {
  MapLibreModule.Logger.setLogCallback((log: any) => {
    console.log(`🌐 [${log.tag}] ${log.message}`);
  });
  log('✅ MapLibre Logger 已启用');
} else {
  log('⚠️ MapLibre Logger 不可用');
}

// ================= 2. 常量 =================
const STYLE_URL = 'https://osm.zhijie.win/styles/bright.json';
const MADRID_COORDS = { longitude: -3.74922, latitude: 40.463667 };

// ================= 组件 =================
export default function OpenStreetMapTestScreen() {
  const cameraRef = useRef<any>(null);
  const [currentCoords, setCurrentCoords] = useState<Location.LocationObjectCoords | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);
  const [libReady] = useState(!!MapLibreModule);

  // 获取定位
  useEffect(() => {
    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      log('📍 定位权限:', status);
      if (status === 'granted') {
        const loc = await Location.getCurrentPositionAsync({});
        setCurrentCoords(loc.coords);
        log('📍 当前位置:', loc.coords.latitude, loc.coords.longitude);
      }
    })();
  }, []);

  // 库未加载时的界面
  if (!libReady) {
    return (
      <ThemedView style={styles.container}>
        <ThemedText style={styles.errorBox}>
          错误: 未找到 @maplibre/maplibre-react-native{'\n'}请执行: npx expo install @maplibre/maplibre-react-native
        </ThemedText>
      </ThemedView>
    );
  }

  // 渲染地图
  log('🗺️ 尝试挂载 MapView');
  return (
    <ThemedView style={styles.container}>
      <View style={styles.mapWrapper}>
        <MapLibreModule.MapView
          style={styles.map}
          styleURL={STYLE_URL} // 🌟 直接使用 URL，让原生引擎带着注入的 Header 去请求
          logoEnabled={false}
          attributionEnabled={true}
          onDidFinishLoadingMap={() => {
            log('🎉 地图加载完成 (onDidFinishLoadingMap)');
            setMapLoaded(true);
            setMapError(null);
          }}
          onDidFailLoadingMap={(e: any) => {
            const err = e?.nativeEvent;
            error('❌ 地图加载失败 (onDidFailLoadingMap)', err);
            setMapError(err?.message || '未知错误');
          }}
          onDidFinishRenderingMap={() => {
            log('✅ 地图完全渲染 (onDidFinishRenderingMap) - 底图已成功绘制！');
          }}
        >
          <MapLibreModule.Camera
            ref={cameraRef}
            defaultSettings={{
              centerCoordinate: [MADRID_COORDS.longitude, MADRID_COORDS.latitude],
              zoomLevel: 9,
            }}
          />
          {currentCoords && (
            <MapLibreModule.ShapeSource
              id="my-location"
              shape={{
                type: 'Feature',
                geometry: {
                  type: 'Point',
                  coordinates: [currentCoords.longitude, currentCoords.latitude],
                },
                properties: {}
              }}
            >
              <MapLibreModule.CircleLayer
                id="my-dot"
                style={{
                  circleColor: '#2563eb',
                  circleRadius: 6,
                  circleStrokeWidth: 2,
                  circleStrokeColor: '#fff'
                }}
              />
            </MapLibreModule.ShapeSource>
          )}
        </MapLibreModule.MapView>
      </View>
      
      <ScrollView style={styles.debugPanel}>
        <ThemedText type="defaultSemiBold">域名: osm.zhijie.win</ThemedText>
        <ThemedText style={styles.statusText}>
          配置: 直接使用 styleURL 获取样式
        </ThemedText>
        <ThemedText style={styles.statusText}>
          地图状态: {mapLoaded ? '✅ 已挂载' : '⏳ 渲染中'}
        </ThemedText>
        {mapError && <ThemedText style={styles.errorBox}>❌ {mapError}</ThemedText>}
        <Pressable onPress={() => {
          log('🕹️ 手动飞往马德里');
          cameraRef.current?.setCamera({ 
            centerCoordinate: [MADRID_COORDS.longitude, MADRID_COORDS.latitude], 
            zoomLevel: 13, 
            animationDuration: 1000 
          });
        }} style={styles.button}>
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