import { useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet } from 'react-native';

import MapLibreGL from '@maplibre/maplibre-react-native';
import * as Location from 'expo-location';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

const OSM_STYLE_URL = 'https://osm.zhijie.win/styles/bright.json';
const SHANGHAI: [number, number] = [121.4737, 31.2304];

export default function OpenStreetMapTestScreen() {
	const [permission, setPermission] = useState<Location.PermissionStatus>('undetermined');
	const hasLocationPermission = useMemo(() => permission === 'granted', [permission]);

	const requestLocationPermission = async () => {
		const result = await Location.requestForegroundPermissionsAsync();
		setPermission(result.status);
	};

	return (
		<ThemedView style={styles.container}>
			<ThemedText type="title" style={styles.title}>
				OpenStreetMap 测试
			</ThemedText>
			<ThemedText>
				这是一个可在 Android 运行的 MapLibre + OpenStreetMap 示例，用于验证地图渲染与定位权限。
			</ThemedText>

			<ThemedView style={styles.mapCard}>
				<MapLibreGL.MapView
					style={styles.map}
					styleURL={OSM_STYLE_URL}
					logoEnabled={false}
					compassEnabled
					rotateEnabled
					zoomEnabled
					surfaceView={Platform.OS === 'android'}>
					<MapLibreGL.Camera zoomLevel={10} centerCoordinate={SHANGHAI} />
					{hasLocationPermission ? (
						<MapLibreGL.UserLocation visible showsUserHeadingIndicator androidRenderMode="normal" />
					) : null}
				</MapLibreGL.MapView>
			</ThemedView>

			<ThemedView style={styles.infoCard}>
				<ThemedText type="defaultSemiBold">定位权限: {permission}</ThemedText>
				<ThemedText numberOfLines={1}>Style: {OSM_STYLE_URL}</ThemedText>
			</ThemedView>

			<Pressable
				onPress={requestLocationPermission}
				style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}>
				<ThemedText type="defaultSemiBold">请求定位权限（显示蓝点）</ThemedText>
			</Pressable>
		</ThemedView>
	);
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		paddingHorizontal: 16,
		paddingTop: 20,
		paddingBottom: 16,
		gap: 12,
	},
	title: {
		fontSize: 28,
		lineHeight: 32,
	},
	mapCard: {
		flex: 1,
		borderRadius: 14,
		overflow: 'hidden',
		borderWidth: 1,
		borderColor: '#9EA7B3',
		minHeight: 300,
	},
	map: {
		flex: 1,
	},
	infoCard: {
		borderWidth: 1,
		borderColor: '#9EA7B3',
		borderRadius: 12,
		padding: 12,
		gap: 2,
	},
	button: {
		borderRadius: 12,
		borderWidth: 1,
		borderColor: '#3E7BFA',
		paddingVertical: 12,
		alignItems: 'center',
	},
	buttonPressed: {
		opacity: 0.75,
	},
});
