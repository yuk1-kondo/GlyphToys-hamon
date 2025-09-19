# Hamon - Glyph Matrix Ripple Wave Toy

Author: YUK_KND

Nothing PhoneのGlyph Matrix用の波紋トイアプリです。中心から同心円の波紋が外へ広がる物理シミュレーションを25×25のLEDマトリックス上で表現します。

## プロジェクト概要

このプロジェクトは、Nothing PhoneのGlyph Matrix上で美しい波紋エフェクトを表示するGlyph Toyアプリケーションです。物理的な波の伝播をシミュレートし、リアルタイムで波紋の動きを表現します。

### 主な特徴

- **物理的な波紋シミュレーション**: 中心から同心円の波紋が外へ伝播
- **長押し機能**: 新しい水滴を落とす + 3種類のプロファイル切替
- **AOD対応**: 省電力モードで毎分ゆっくり波が進む
- **複数波源**: 最大3つの水滴を同時に表示可能
- **円形マスク**: 25×25の正方ではなく、実表示の円に合わせた表示

## 波紋プロファイル

Hamonアプリでは3つの異なる波紋プロファイルを提供しています：

1. **柔らかめ**: 波長4.0px、速度0.22px/frame、減衰0.06
2. **くっきり・速い**: 波長3.0px、速度0.35px/frame、減衰0.05
3. **ゆったり・減衰強**: 波長5.5px、速度0.16px/frame、減衰0.08

## インストール方法

### 前提条件

- Nothing Phone (Phone 3)
- Android Studio（開発用）
- Glyph Matrix SDK 1.0

### APKインストール

1. リリースされたAPKファイル（`Hamon-device-release-signed.apk`）をダウンロード
2. Nothing Phoneの設定で「不明なアプリのインストール」を許可
3. APKファイルをタップしてインストール
4. Glyph Toys設定から「Hamon」を選択

### 開発環境でのビルド

#### エミュレーター用（推奨）
1. プロジェクトをAndroid Studioで開く
2. エミュレーターを起動
3. ビルドして実行
4. シミュレーターで波紋の動作を確認

#### 実機用
1. プロジェクトをAndroid Studioで開く
2. Glyph Matrix SDKのAARファイルが`app/libs/`に配置されていることを確認
3. ビルドしてAPKを生成
4. Nothing Phoneにインストール

## 使い方

### 基本的な操作

1. **アプリをインストール**後、Glyph Toys設定から「Hamon」を選択
2. **背面のGlyph Button**でトイを切り替え
3. **長押し**で新しい水滴追加 + プロファイル切替
4. **AOD設定**で省電力モード

### 操作詳細

- **短押し**: Glyph Buttonを短く押すと、利用可能なトイ間を切り替え
- **長押し**: 長く押すと新しい水滴を追加し、3つのプロファイルを順次切り替え
- **AODモード**: 省電力モードで毎分ゆっくりと波が進む

## エミュレーターでの動作

エミュレーターでは以下の機能が利用可能です：

- **25×25グリッド表示**: 波紋を500×500ピクセルの画面に拡大表示
- **プロファイル切替**: 3つの波紋プロファイルを切り替え
- **水滴追加**: 長押しボタンで新しい水滴を追加
- **AODシミュレーション**: AODボタンで1ステップずつ波を進める

実機のGlyph Matrixと同じ物理計算を使用しているため、動作確認に最適です。

## 技術仕様

### 物理計算

Hamonアプリは以下の物理計算を使用して波紋をシミュレートしています：

- **位相計算**: `位相 = 2π × ((r - v×t) / λ)`
  - `r`: 中心からの距離
  - `v`: 波の速度
  - `t`: 時間
  - `λ`: 波長

- **減衰**: 距離に対して`exp(-α×r)`、時間に対して`envelope(age)`
- **合成**: 複数の水滴を加算合成
- **円マスク**: 実表示の円に合わせて縁をソフトに

### パフォーマンス

- **フレームレート**: 60FPS（通常モード）、1FPM（AODモード）
- **最大水滴数**: 3つ同時表示
- **計算負荷**: 25×25マトリックスでのリアルタイム計算

## カスタマイズ

`RippleWaveToyService.java`（Hamon Toy）内の以下のパラメータを調整可能：

- `profiles[]`: 波長、速度、減衰の設定
- `envelope()`: 時間経過による立ち上がり・減衰カーブ
- `base/scale`: ベース輝度とコントラスト
- `RADIUS`: 描画円の大きさ

### カスタマイズ例

```java
// 新しいプロファイルを追加
private static final Profile[] profiles = {
    new Profile(4.0f, 0.22f, 0.06f),  // 柔らかめ
    new Profile(3.0f, 0.35f, 0.05f),  // くっきり・速い
    new Profile(5.5f, 0.16f, 0.08f),  // ゆったり・減衰強
    new Profile(2.5f, 0.40f, 0.04f)   // 新規追加
};
```

## ライセンス

このプロジェクトはGlyph Matrix Developer Kitを使用して作成されています。

## 関連リソース

- [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Matrix-Developer-Kit)
- [GlyphMatrix-Example-Project](https://github.com/KenFeng04/GlyphMatrix-Example-Project)
- [Nothing Community](https://nothing.community/t/glyph-sdk)

## サポート

問題やバグを発見した場合は、GitHubのIssuesで報告してください。

開発に関する質問は以下まで：
- [GDKsupport@nothing.tech](mailto:GDKsupport@nothing.tech)
- [Nothing Community](https://nothing.community/t/glyph-sdk)