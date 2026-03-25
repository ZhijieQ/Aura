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

const VECTOR_HOST = '192.168.1.96';
const VECTOR_PATH = '/combined?token=my_secret_key';
const VECTOR_STYLE_URL = `http://${VECTOR_HOST}:8080${VECTOR_PATH}`;
const LOG_PREFIX = '[OpenStreetMapTest]';

type PermissionState = 'unknown' | 'granted' | 'denied';

function getMapLibreModule() {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    return require('@maplibre/maplibre-react-native') as typeof import('@maplibre/maplibre-react-native');
  } catch {
    return null;
  }
}

function resolveTiles(tiles: string[], fallbackStyleUrl: string) {
  const fallback = new URL(fallbackStyleUrl);

  return tiles.map((tileUrl) => {
    try {
      const parsed = new URL(tileUrl, fallback.origin);
      if (!parsed.port && fallback.port) {
        parsed.port = fallback.port;
      }

      return parsed.toString();
    } catch {
      return tileUrl;
    }
  });
}

function makeGeneratedStyle(tilejson: any, fallbackStyleUrl: string) {
  const sources = {
    'local-vector': {
      type: 'vector',
      tiles: resolveTiles(tilejson.tiles, fallbackStyleUrl),
      minzoom: tilejson.minzoom ?? 0,
      maxzoom: tilejson.maxzoom ?? 14,
    },
  };

  const layers: any[] = [
    {
      id: 'background',
      type: 'background',
      paint: { 'background-color': '#f1f5f9' },
    },
  ];

  // 定义图层渲染顺序和样式
  const layerConfigs = [
    { id: 'landcover', type: 'fill', color: '#dcfce7' }, // 绿地
    { id: 'landuse', type: 'fill', color: '#f8fafc' },   // 土地利用
    { id: 'water', type: 'fill', color: '#bae6fd' },    // 水系
    { id: 'boundary', type: 'line', color: '#94a3b8', width: 1 }, // 边界
    { id: 'transportation', type: 'line', color: '#ffffff', width: 2 }, // 道路
    { id: 'building', type: 'fill', color: '#e2e8f0', opacity: 0.8 }, // 建筑物
    { id: 'place', type: 'circle', color: '#64748b', radius: 3 }, // 地名点
  ];

  layerConfigs.forEach((config) => {
    // 检查 TileJSON 是否包含这个图层
    if (tilejson.vector_layers.some((l: any) => l.id === config.id)) {
      const layer: any = {
        id: `layer-${config.id}`,
        source: 'local-vector',
        'source-layer': config.id,
        type: config.type,
        paint: {},
      };

      if (config.type === 'fill') {
        layer.paint['fill-color'] = config.color;
        layer.paint['fill-opacity'] = config.opacity ?? 1;
      } else if (config.type === 'line') {
        layer.paint['line-color'] = config.color;
        layer.paint['line-width'] = config.width;
      } else if (config.type === 'circle') {
        layer.paint['circle-color'] = config.color;
        layer.paint['circle-radius'] = config.radius;
      }

      layers.push(layer);
    }
  });

  return { version: 8, sources, layers };
}

export default function OpenStreetMapTestScreen() {
  const cameraRef = useRef<any>(null);
  const [permissionState, setPermissionState] = useState<PermissionState>('unknown');
  const [currentCoords, setCurrentCoords] = useState<Location.LocationObjectCoords | null>(null);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);
  const [mapStyleJson, setMapStyleJson] = useState<string | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);
  const [requesting, setRequesting] = useState(false);
  const maplibreModule = getMapLibreModule();
  const vectorStyleUrl = VECTOR_STYLE_URL;

  const loadMapStyle = async () => {
    console.log(LOG_PREFIX, 'loadMapStyle start', vectorStyleUrl);
    setMapError(null);

    try {
      const response = await fetch(vectorStyleUrl);
      if (!response.ok) {
        throw new Error(`样式请求失败: HTTP ${response.status}`);
      }

      const data = await response.json();
      const hasStyleLayers = data && data.version && Array.isArray(data.layers);

      if (hasStyleLayers) {
        console.log(LOG_PREFIX, 'detected style json response');
        setMapStyleJson(JSON.stringify(data));
      } else {
        console.log(LOG_PREFIX, 'detected tilejson response, generate style');
        const generatedStyle = makeGeneratedStyle(data, vectorStyleUrl);
        setMapStyleJson(JSON.stringify(generatedStyle));
      }
    } catch (error) {
      console.error(LOG_PREFIX, 'loadMapStyle error', error);
      setMapError(error instanceof Error ? error.message : '地图样式初始化失败');
      setMapStyleJson(null);
    }
  };

  const requestPermissionAndLocate = async () => {
    console.log(LOG_PREFIX, 'requestPermissionAndLocate start');
    setRequesting(true);
    setLocationError(null);

    try {
      const servicesEnabled = await Location.hasServicesEnabledAsync();
      if (!servicesEnabled) {
        console.error(LOG_PREFIX, 'location services disabled');
        setCurrentCoords(null);
        setLocationError('定位服务未开启，请先开启系统定位服务后重试。');
        return;
      }

      const permission = await Location.requestForegroundPermissionsAsync();
      console.log(LOG_PREFIX, 'permission status', permission.status);

      if (permission.status !== 'granted') {
        console.error(LOG_PREFIX, 'permission denied');
        setPermissionState('denied');
        setCurrentCoords(null);
        setLocationError('定位权限未授权，请在系统设置中允许定位权限。');
        return;
      }

      setPermissionState('granted');
      let current = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      }).catch(async () => {
        console.error(LOG_PREFIX, 'getCurrentPositionAsync failed, try lastKnown');
        const lastKnown = await Location.getLastKnownPositionAsync();
        if (!lastKnown) {
          throw new Error('Current location is unavailable');
        }

        setLocationError('当前实时定位不可用，已使用最近一次已知位置。');
        return lastKnown;
      });

      setCurrentCoords(current.coords);
      console.log(LOG_PREFIX, 'resolved coords', current.coords.latitude, current.coords.longitude);

      if (mapLoaded) {
        cameraRef.current?.setCamera({
          centerCoordinate: [current.coords.longitude, current.coords.latitude],
          zoomLevel: 13,
          animationDuration: 500,
        });
      }
    } catch (error) {
      console.error(LOG_PREFIX, 'requestPermissionAndLocate error', error);
      setCurrentCoords(null);
      setLocationError(error instanceof Error ? `定位失败：${error.message}` : '定位失败，请稍后重试。');
    } finally {
      console.log(LOG_PREFIX, 'requestPermissionAndLocate end');
      setRequesting(false);
    }
  };

  useEffect(() => {
    void requestPermissionAndLocate();
  }, []);

  useEffect(() => {
    void loadMapStyle();
  }, []);

  useEffect(() => {
    if (!currentCoords || !mapLoaded) {
      return;
    }

    cameraRef.current?.setCamera({
      centerCoordinate: [currentCoords.longitude, currentCoords.latitude],
      zoomLevel: 13,
      animationDuration: 500,
    });
  }, [currentCoords, mapLoaded]);

  if (Platform.OS === 'web') {
    return (
      <ThemedView style={styles.container}>
        <ThemedText type="title">OpenStreetMap 测试（Web）</ThemedText>
        <ThemedText>请访问 /tests/openstreetmap 路由的 web 版本页面进行测试。</ThemedText>
      </ThemedView>
    );
  }

  if (!maplibreModule) {
    return (
      <ThemedView style={styles.container}>
        <ThemedText type="title">OpenStreetMap 测试</ThemedText>
        <ThemedText>
          当前运行环境未加载到 @maplibre/maplibre-react-native。请确认依赖安装、插件配置并重新构建客户端后重试。
        </ThemedText>
      </ThemedView>
    );
  }

  const MapLibre = (maplibreModule as any).default ?? maplibreModule;
  const MapView = MapLibre.MapView;
  const Camera = MapLibre.Camera;
  const ShapeSource = MapLibre.ShapeSource;
  const CircleLayer = MapLibre.CircleLayer;

  const currentLocationGeoJson = currentCoords
    ? {
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            geometry: {
              type: 'Point',
              coordinates: [currentCoords.longitude, currentCoords.latitude],
            },
            properties: {},
          },
        ],
      }
    : null;

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <ThemedText type="title">MapLibre 测试</ThemedText>
        <ThemedText>地图地址：{vectorStyleUrl}</ThemedText>
        <ThemedText>
          当前权限：
          {permissionState === 'granted' ? '已授权' : permissionState === 'denied' ? '已拒绝' : '待确认'}
        </ThemedText>
      </ThemedView>

      <View style={styles.mapWrapper}>
        {mapStyleJson ? (
          <MapView
            style={styles.map}
            styleJSON={mapStyleJson}
            onDidFailLoadingMap={(event: any) => {
              const message = event?.message ?? 'MapLibre 加载失败';
              console.error(LOG_PREFIX, 'onDidFailLoadingMap', message, event);
              setMapError(message);
            }}
            onDidFinishLoadingMap={() => {
              console.log(LOG_PREFIX, 'onDidFinishLoadingMap');
              setMapLoaded(true);
              setMapError(null);
            }}>
            <Camera
              ref={cameraRef}
              defaultSettings={{
                centerCoordinate: [DEFAULT_REGION.longitude, DEFAULT_REGION.latitude],
                zoomLevel: 10,
              }}
            />

            {currentLocationGeoJson ? (
              <ShapeSource id="current-location-source" shape={currentLocationGeoJson as any}>
                <CircleLayer
                  id="current-location-ring"
                  style={{
                    circleColor: '#2563eb',
                    circleOpacity: 0.2,
                    circleRadius: 12,
                    circleStrokeColor: '#1d4ed8',
                    circleStrokeWidth: 1,
                  }}
                />
                <CircleLayer
                  id="current-location-dot"
                  style={{
                    circleColor: '#2563eb',
                    circleRadius: 5,
                    circleStrokeColor: '#ffffff',
                    circleStrokeWidth: 2,
                  }}
                />
              </ShapeSource>
            ) : null}
          </MapView>
        ) : (
          <View style={[styles.map, styles.mapPlaceholder]}>
            <ThemedText>地图样式加载中...</ThemedText>
          </View>
        )}
      </View>

      {currentCoords ? (
        <ThemedText>
          当前位置：{currentCoords.latitude.toFixed(6)}, {currentCoords.longitude.toFixed(6)}
        </ThemedText>
      ) : null}

      {locationError ? <ThemedText>{locationError}</ThemedText> : null}
      {mapError ? <ThemedText>地图错误：{mapError}</ThemedText> : null}

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
  mapPlaceholder: {
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f2f4f7',
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
