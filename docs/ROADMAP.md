# Civillagers — Roadmap

Mục tiêu: làng tự phát triển thành nền văn minh với **lính gác**, **trưởng làng** và **thợ xây**, mà
**không làm nặng server**. Hiệu năng và bộ nhớ là yêu cầu bắt buộc, không phải việc "tối ưu sau":
mỗi giai đoạn chỉ xong khi đạt ngân sách hiệu năng của nó.

---

## 1. Nguyên tắc hiệu năng và bộ nhớ (áp dụng cho mọi giai đoạn)

### 1.1 Không memory leak: mọi trạng thái đều có chủ sở hữu và vòng đời

Leak trong mod Minecraft gần như luôn đến từ một chỗ: **một object sống lâu (static, singleton,
cache) giữ tham chiếu tới thứ thuộc về một world** (`Entity`, `Level`, `ServerPlayer`, `LevelChunk`,
`MinecraftServer`). Trong singleplayer, mỗi lần thoát rồi vào lại world là một server mới, nên thứ bị
giữ lại sẽ chồng lên nhau sau mỗi lần như vậy.

| Quy tắc | Cách làm |
|---|---|
| **Không static giữ dữ liệu world** | Dữ liệu làng nằm trong `SavedData` của từng `ServerLevel`, nên được giải phóng cùng level. Không có `static Map<..., Village>`. |
| **Lưu UUID, không lưu Entity** | Làng lưu `UUID` của dân và lính gác. Khi cần thì tra `ServerLevel.getEntity(uuid)`; kết quả chỉ giữ trong một tick. |
| **Cache nào cũng có lệnh xóa** | Mọi cache tạm đều được dọn ở `Loader.onServerStopped` (và `onClientDisconnect` phía client). Viết cache mới thì viết luôn dòng xóa. |
| **Không danh sách tăng mãi** | Lịch sử, nhật ký, "kẻ thù đã thấy"… dùng ring buffer hoặc có giới hạn cứng. Danh sách không giới hạn là leak trá hình. |
| **Listener đăng ký một lần** | Event listener chỉ đăng ký lúc khởi tạo mod, không bao giờ đăng ký theo từng entity hay từng làng. |
| **Không `WeakReference` để chữa cháy** | Cách sửa đúng là sở hữu rõ ràng, không phải chờ GC đoán hộ. |
| **Không force-load chunk** | Không tạo chunk ticket. Thợ xây chỉ làm việc ở chunk đã load sẵn và tiếp tục khi chunk được load lại. |

### 1.2 Tick rẻ: làm ít, làm thưa, chia nhỏ

| Quy tắc | Cách làm |
|---|---|
| **Quyết định chậm** | Trưởng làng "suy nghĩ" mỗi ~100 tick (5 giây), không phải mỗi tick. |
| **Rải đều theo thời gian** | Làng thứ *i* chạy ở tick `(gameTime + hash(id)) % interval == 0`, để các làng không dồn vào cùng một tick. |
| **Ngân sách mỗi tick** | `VillageScheduler` có giới hạn số làng xử lý mỗi tick và giới hạn thời gian (đo bằng `System.nanoTime`, mặc định 0.5 ms). Hết ngân sách thì phần còn lại sang tick sau. Có thể chỉnh trong config. |
| **Quét thế giới có nhịp** | Lính gác tìm mục tiêu mỗi 10–20 tick, trong một AABB giới hạn (`getEntitiesOfClass`), không quét mỗi tick. |
| **Pathfinding tiết kiệm** | Chỉ tính lại đường khi mục tiêu di chuyển quá N block; giới hạn số lần tính đường trong cùng một tick. |
| **Tra cứu theo vị trí** | Chỉ mục không gian `Long2IntOpenHashMap` (chunk → id làng), không duyệt qua mọi làng để hỏi "vị trí này thuộc làng nào". |
| **Dùng heightmap** | Chọn chỗ xây bằng `Heightmap` và lưới thưa, không quét từng block. |
| **Chỉ dùng main thread** | World của Minecraft không thread-safe. Luồng phụ chỉ dùng cho tính toán thuần trên dữ liệu đã chụp lại (snapshot), không bao giờ đọc hay ghi world. |

### 1.3 Ít cấp phát trong đường nóng (hot path)

- Trong vòng lặp chạy mỗi tick: dùng lại `BlockPos.MutableBlockPos`; không dùng stream hay lambda bắt
  biến (capturing lambda); tránh boxing (dùng collection của fastutil, vốn có sẵn trong Minecraft).
- Bản thiết kế công trình (`.nbt`) được nạp **một lần** qua `StructureTemplateManager`, vì vanilla đã
  cache sẵn. Tiến độ xây chỉ là con trỏ gọn `(templateId, index)`, không chép danh sách block.
- Dữ liệu đồng bộ cho client: chỉ gửi khi có thay đổi, chỉ cho người cần (ví dụ người đang mở GUI làng),
  có giới hạn tần suất. Không gửi packet mỗi tick.
- `SavedData.setDirty()` chỉ gọi khi dữ liệu thật sự đổi. Dữ liệu lưu dùng `Codec` gọn.

### 1.4 Đo, không đoán

| Công cụ | Dùng để |
|---|---|
| `runBenchmark` (chuyển từ ThirstWasTaken2) | Đo mspt (mili-giây mỗi tick) mà mod chiếm, với kịch bản 1 / 10 / 50 làng, trên seed cố định |
| JFR (`-Pprofile`) | Tìm hàm nóng và nơi cấp phát nhiều |
| Kiểm tra leak | Mở/đóng world singleplayer 5 lần, chạy `jcmd <pid> GC.class_histogram`: số instance `ServerLevel` và các class của mod phải về 0 |
| Gametest | Mỗi quy tắc vòng đời có test: tạo làng, tắt server, kiểm tra mọi cache đã trống |
| `/civillagers debug stats` | Lệnh dev: số làng, thời gian tick trung bình và tối đa của scheduler, kích thước các cache |

**Ngân sách mục tiêu** (server, mức tham chiếu khi đo bằng benchmark):

| Kịch bản | Ngân sách |
|---|---|
| 10 làng, ~100 dân, ~20 lính, 3 thợ xây đang làm | ≤ 0.5 ms/tick trung bình, ≤ 2 ms tối đa |
| Một làng nằm ở chunk không load | ~0 ms (không tick) |
| Dữ liệu một làng | ≤ ~4 KB trong bộ nhớ |
| Sau 5 lần mở/đóng world | 0 instance `ServerLevel` còn sót |

---

## 2. Các giai đoạn

### Giai đoạn 0 — Nền móng ✅
- Cấu trúc nhiều phiên bản (Fabric 26.3, 26.2, 1.21.1, NeoForge 1.21.1), CI, gametest, bump dependency tự động.

### Giai đoạn 1 — Lõi làng và hạ tầng đo
*Không có tính năng người chơi nhìn thấy, nhưng mọi thứ sau đều xây trên phần này.*
- `VillageData` (`SavedData` cho từng dimension): id, tâm làng, bán kính, danh sách UUID dân, cấp độ, kho tài nguyên (con số, không phải item).
- Nhận diện làng từ POI vanilla (chuông, giường, workstation); gộp và tách làng.
- `VillageIndex`: chỉ mục chunk → làng.
- `VillageScheduler`: tick rải đều, có ngân sách, chỉ xử lý làng có chunk đang load.
- Các hook vòng đời trong `platform/Loader`: `onServerStarted`, `onServerStopped`, `onServerTickEnd`.
- Chuyển `runBenchmark` và `/civillagers debug stats` từ ThirstWasTaken2 sang.
- **Xong khi:** gametest tạo, lưu, nạp lại và gộp làng đều pass; benchmark 50 làng rỗng ≤ 0.1 ms/tick; test leak đạt.

### Giai đoạn 2 — Lính gác (Guard)
- Entity `Guard` riêng (không phải profession của villager, vì nghề vanilla chỉ đổi giao dịch).
- Dùng `GoalSelector` (rẻ, dễ kiểm soát) thay cho `Brain` đầy đủ.
- Tuần tra quanh làng, tấn công quái thù địch, bảo vệ khi có raid, về giường ban đêm.
- Trang bị và áo giáp; người chơi có thể đưa đồ.
- Model và renderer (Blockbench), lang `en_us` và `vi_vn`.
- **Xong khi:** 20 lính gác ≤ 0.15 ms/tick; quét mục tiêu ≤ 1 lần mỗi 10 tick cho mỗi lính; gametest chiến đấu và tuần tra pass.

### Giai đoạn 3 — Trưởng làng (Chief)
- Mỗi làng có một trưởng làng, được chọn từ dân làng và kế nhiệm khi trưởng làng chết.
- Vòng quyết định chậm (~5 giây, rải đều) dựa trên mô hình **nhu cầu**: nhà ở, phòng thủ, lương thực, mở rộng.
- Kết quả là một **kế hoạch** ghi vào `VillageData`, ví dụ "cần thêm 1 nhà" hay "cần thêm 2 lính".
- GUI xem thông tin làng; chỉ sync khi GUI đang mở.
- **Xong khi:** quyết định của trưởng làng ≤ 0.05 ms mỗi làng mỗi lần chạy; gametest kế nhiệm và nhu cầu pass.

### Giai đoạn 4 — Thợ xây (Builder)
- Thư viện công trình `.nbt` trong `data/civillagers/structure/` (nhà, tường, tháp canh, nông trại), mở rộng được bằng datapack.
- Chọn chỗ xây bằng heightmap và lưới thưa, rải qua nhiều tick.
- Đặt block dần dần: tối đa *K* block mỗi tick cho mỗi thợ và một giới hạn chung, chỉ ở chunk đã load, lưu con trỏ tiến độ để xây tiếp sau khi tải lại.
- (Tùy chọn) Xây tốn tài nguyên trong kho làng.
- **Xong khi:** 3 thợ xây cùng lúc ≤ 0.2 ms/tick; thoát game giữa chừng rồi vào lại thì xây tiếp đúng chỗ; không chunk nào bị force-load.

### Giai đoạn 5 — Làng phát triển (Growth)
- Cấp độ làng và các thời kỳ: gỗ → đá → gạch → tường thành.
- Trưởng làng mở khóa công trình mới theo cấp; dân số tăng theo số nhà ở.
- **Xong khi:** benchmark 10 làng cấp tối đa vẫn trong ngân sách ở mục 1.4.

### Giai đoạn 6 — Hoàn thiện và phát hành 1.0
- Màn hình config (Mod Menu / NeoForge), toàn bộ giới hạn hiệu năng chỉnh được.
- Tài liệu, trang Modrinth/CurseForge, script phát hành (chuyển từ ThirstWasTaken2).
- Thử tương thích với các mod villager phổ biến (MCA, Guard Villagers) và mod hiệu năng (Lithium, ModernFix).

### Sau 1.0 (ý tưởng)
- Quan hệ giữa các làng: giao thương, liên minh, chiến tranh.
- Tính cách trưởng làng (hiếu chiến / thương mại / xây dựng).
- NeoForge 26.x khi nhu cầu đủ lớn.

---

## 3. Câu hỏi cần chốt trước Giai đoạn 2–4

1. Lính gác là **entity riêng** (đề xuất) hay **villager đổi nghề**?
2. Thợ xây có **tốn tài nguyên** không, hay xây miễn phí theo thời gian?
3. Người chơi **ra lệnh** được cho làng (qua trưởng làng) hay làng hoàn toàn tự chủ?
4. Làng tự sinh ra đã có lính gác/trưởng làng, hay phải đạt điều kiện mới có?
