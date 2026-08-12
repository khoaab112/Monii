# QUY TẮC PHÁT TRIỂN DỰ ÁN (AGENT RULES)

Tài liệu này định nghĩa các nguyên tắc bắt buộc đối với AI Agent khi phân tích, sửa đổi, mở rộng và tối ưu codebase của ứng dụng.

Đây là ứng dụng mobile dùng để **ghi chép, quản lý và theo dõi lịch sử thu chi cá nhân**. Vì vậy, tính chính xác của dữ liệu, tính ổn định, khả năng bảo trì và tính nhất quán của trải nghiệm người dùng được ưu tiên cao hơn việc hoàn thành một yêu cầu bằng mọi giá.

---

# 1. NGUYÊN TẮC CỐT LÕI

## 1.1. Agent không phải là executor mù quáng

Agent không được mặc định rằng mọi yêu cầu hoặc phương án implementation do người dùng đưa ra đều là phương án kỹ thuật tốt nhất.

Trước khi thay đổi code, Agent phải suy xét:

* Yêu cầu có phù hợp với kiến trúc hiện tại không?
* Có component hoặc logic tương tự đã tồn tại không?
* Có thể tái sử dụng implementation hiện tại không?
* Có cách đơn giản hơn không?
* Thay đổi này có tạo duplication không?
* Có phá vỡ behavior hiện tại không?
* Có ảnh hưởng đến dữ liệu người dùng không?
* Có tạo technical debt không?
* Có thể giải quyết vấn đề ở phạm vi nhỏ hơn không?
* Có ảnh hưởng đến performance, lifecycle hoặc state của ứng dụng không?

Nếu nhận thấy yêu cầu có vấn đề về kỹ thuật, kiến trúc, UX hoặc khả năng bảo trì, Agent phải **chỉ ra vấn đề và phản biện một cách rõ ràng**, đồng thời đưa ra phương án thay thế phù hợp hơn.

Không được âm thầm thực hiện một phương án mà Agent biết có vấn đề nghiêm trọng.

Tuy nhiên, Agent không được phản biện một cách máy móc. Nếu người dùng đã hiểu rõ trade-off và xác nhận muốn thực hiện phương án đó, Agent có thể thực hiện theo quyết định cuối cùng của người dùng.

---

## 1.2. Hiểu trước khi sửa

Không được bắt đầu bằng việc viết code ngay lập tức.

Trước khi thay đổi code, Agent phải xác định:

1. File/màn hình/component liên quan.
2. Luồng dữ liệu hiện tại.
3. Component hoặc utility đang được sử dụng.
4. State và lifecycle liên quan.
5. Navigation liên quan.
6. ViewModel/Repository/Database liên quan nếu có.
7. Các implementation tương tự đang tồn tại trong project.

Mục tiêu là **thay đổi đúng nơi, với phạm vi nhỏ nhất cần thiết**.

---

# 2. REUSE BEFORE CREATE

## 2.1. Nguyên tắc ưu tiên tái sử dụng

Đây là một trong những nguyên tắc quan trọng nhất của dự án.

**Không được tạo code mới nếu code hiện tại có thể được tái sử dụng hoặc mở rộng một cách hợp lý.**

Trước khi tạo component, helper, formatter, validator, state handler hoặc business logic mới, Agent phải kiểm tra implementation hiện tại.

Thứ tự ưu tiên:

```text
1. Reuse existing component / logic
2. Reuse existing component với configuration khác
3. Mở rộng component / logic hiện tại
4. Refactor implementation hiện tại thành shared component nếu hợp lý
5. Chỉ tạo implementation mới khi các phương án trên không phù hợp
```

---

## 2.2. Không duplicate component

Không được tạo nhiều component có cùng bản chất chỉ vì chúng xuất hiện ở các màn hình khác nhau.

Ví dụ không nên tồn tại:

```text
ExpenseCard()
ExpenseHistoryCard()
TransactionCard()
RecentExpenseCard()
```

nếu chúng thực chất biểu diễn cùng một loại dữ liệu và chỉ khác cách cấu hình.

Trong trường hợp phù hợp, nên thiết kế theo hướng:

```text
TransactionCard(
    transaction = ...,
    variant = ...,
    ...
)
```

hoặc một abstraction tương đương.

---

## 2.3. Không duplicate business logic

Các logic như:

* format tiền
* format ngày tháng
* tính tổng thu/chi
* validation
* phân loại transaction
* xử lý trạng thái giao dịch
* chuyển đổi model
* xử lý filter/sort

không được copy giữa nhiều màn hình.

Nếu một logic có khả năng được sử dụng ở nhiều nơi, phải cân nhắc đưa nó về abstraction phù hợp.

---

# 3. REUSABLE CODE & ABSTRACTION

## 3.1. Code phải có khả năng tái sử dụng

Khi tạo implementation mới, Agent phải cân nhắc khả năng tái sử dụng trong tương lai.

Component nên được thiết kế dựa trên **behavior và responsibility**, không dựa trên một màn hình duy nhất.

Ví dụ:

```kotlin
TransactionItem(
    transaction = transaction,
    onClick = onClick,
    modifier = modifier
)
```

thay vì để component phụ thuộc trực tiếp vào một ViewModel hoặc một màn hình cụ thể.

---

## 3.2. Không over-engineering

Reusable không có nghĩa là mọi đoạn code đều phải được abstraction.

Không được tạo abstraction chỉ vì:

* Muốn giảm vài dòng code.
* Muốn chia nhỏ file một cách máy móc.
* Component chỉ được sử dụng một lần và không có boundary rõ ràng.
* Tạo interface/factory/generic phức tạp không cần thiết.

Nguyên tắc:

> **Create abstractions when they provide meaningful reuse, separation of responsibility, consistency, or future extensibility.**

Không đánh đổi sự đơn giản để lấy một abstraction không cần thiết.

---

# 4. KIẾN TRÚC CODE

## 4.1. Tôn trọng kiến trúc hiện tại

Không tự ý thay đổi kiến trúc cốt lõi của project nếu người dùng không yêu cầu hoặc thay đổi đó không thực sự cần thiết.

Không được thực hiện các refactor lớn chỉ để “code đẹp hơn”.

Nếu phát hiện kiến trúc hiện tại có vấn đề:

1. Nêu vấn đề.
2. Giải thích tác động.
3. Đề xuất phương án.
4. Chỉ refactor khi phạm vi và lợi ích hợp lý.

---

## 4.2. Separation of Concerns

Mỗi tầng phải có trách nhiệm rõ ràng.

UI không nên trực tiếp:

* truy cập Database;
* chứa business logic phức tạp;
* thực hiện các thao tác persistence;
* chứa logic xử lý dữ liệu có thể tái sử dụng.

Các thao tác nên tuân thủ luồng tương ứng với kiến trúc hiện tại của project, ví dụ:

```text
UI
 ↓
ViewModel / Presentation Logic
 ↓
Repository / Domain Logic
 ↓
Data Source / Database
```

Không được phá vỡ luồng này chỉ để giải quyết nhanh một feature.

---

# 5. QUY TẮC DỮ LIỆU CHI TIÊU

Vì đây là ứng dụng quản lý tài chính cá nhân, **tính chính xác của dữ liệu được ưu tiên cao**.

## 5.1. Tiền tệ

Không được sử dụng floating-point không phù hợp để lưu trữ hoặc tính toán số tiền nếu có nguy cơ gây sai số.

Các phép tính tiền phải đảm bảo:

* không mất precision;
* không tự ý làm tròn;
* thống nhất đơn vị tiền tệ;
* thống nhất quy tắc format.

Không được tự ý thay đổi cách lưu trữ số tiền trong database nếu chưa đánh giá ảnh hưởng đến dữ liệu hiện tại.

---

## 5.2. Dữ liệu giao dịch

Các thao tác:

* tạo transaction;
* chỉnh sửa transaction;
* xóa transaction;
* thay đổi category;
* thay đổi amount;
* thay đổi ngày giao dịch;

phải đảm bảo dữ liệu liên quan được cập nhật nhất quán.

Không được thực hiện thao tác có nguy cơ làm mất hoặc làm sai dữ liệu người dùng.

---

## 5.3. Database Migration

Khi thay đổi schema database:

* Phải kiểm tra migration hiện tại.
* Không được xóa hoặc thay đổi dữ liệu hiện có một cách tùy tiện.
* Phải đảm bảo database cũ có thể nâng cấp lên schema mới.
* Không được reset database chỉ để làm cho development dễ dàng hơn nếu có nguy cơ ảnh hưởng dữ liệu người dùng.

---

# 6. UI / UX

## 6.1. Design System

Ưu tiên tuyệt đối các component, theme, typography, spacing và color đã tồn tại trong project.

Không tự tạo style riêng nếu project đã có design system tương ứng.

---

## 6.2. Material Theme

Phải ưu tiên:

```kotlin
MaterialTheme.colorScheme
```

và các token/theme hiện có.

Hạn chế hard-code HEX color.

Chỉ sử dụng màu hard-code khi:

* thiết kế đã chỉ định rõ;
* hoặc có lý do kỹ thuật/UX chính đáng.

UI phải hỗ trợ đúng Light/Dark Mode theo design system hiện tại.

---

# 7. APP HEADER

Nếu màn hình sử dụng `AppHeader` chung từ `MainActivity`, phải ưu tiên sử dụng `AppHeader`.

Chỉ tạo `TopAppBar` riêng khi thực sự cần thiết.

Nếu bắt buộc phải tạo `TopAppBar` riêng, phải giữ đồng nhất với `AppHeader`:

```kotlin
Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 3.dp,
    tonalElevation = 1.dp
)
```

Title:

```kotlin
fontWeight = FontWeight.Black
fontSize = 20.sp
letterSpacing = 0.5.sp
color = MaterialTheme.colorScheme.onSurface
```

Không được tạo một style Header mới nếu chỉ khác biệt không đáng kể.

---

# 8. MODAL BOTTOM SHEET

Tất cả ModalBottomSheet trong project phải ưu tiên sử dụng component chung:

```text
com.app.ui.components.AppModalBottomSheet
```

Không tạo ModalBottomSheet riêng nếu `AppModalBottomSheet` có thể đáp ứng nhu cầu.

Sheet sử dụng:

```kotlin
rememberModalBottomSheetState(
    skipPartiallyExpanded = true
)
```

Cấu trúc chuẩn:

```text
Header
 ├── Title
 ├── Optional Action
 ├── Close Action
 └── Divider

Content

Footer
 └── Primary / Secondary Actions
```

Title:

```text
20.sp
FontWeight.Black
```

Nếu một sheet có cấu trúc khác, Agent phải đánh giá trước xem đó là một yêu cầu UX thực sự hay chỉ là implementation khác biệt không cần thiết.

---

# 9. FORM & VALIDATION

Validation phải rõ ràng và trực tiếp.

Khi input không hợp lệ:

* phải hiển thị lỗi gần input;
* phải sử dụng trạng thái `isError = true` khi component hỗ trợ;
* thông báo lỗi phải giải thích được vấn đề;
* không được chỉ hiển thị một thông báo lỗi chung ở nơi khác.

Ví dụ:

```kotlin
supportingText = {
    Text(
        text = "...",
        color = MaterialTheme.colorScheme.error
    )
}
```

Validation phải được thiết kế để có thể tái sử dụng thay vì copy logic giữa các form.

---

# 10. STATE MANAGEMENT

UI phải phản ánh đúng state của ứng dụng.

Agent phải cân nhắc đầy đủ các trạng thái:

```text
Loading
Success
Empty
Error
```

Không được mặc định rằng dữ liệu luôn tồn tại.

Đối với màn hình danh sách transaction, phải xử lý rõ:

* chưa có dữ liệu;
* đang tải dữ liệu;
* tải dữ liệu thất bại;
* danh sách có dữ liệu;
* filter không trả về kết quả.

Không được sử dụng state tạm thời theo cách gây mất dữ liệu khi recomposition hoặc navigation.

---

# 11. NAVIGATION

Navigation phải tôn trọng cấu trúc hiện tại của project.

Không tạo navigation mechanism mới nếu project đã có mechanism chung.

Khi quay lại màn hình:

* state hợp lý phải được giữ lại;
* filter/search/sort không được reset vô lý;
* dữ liệu không được reload không cần thiết;
* animation không được chạy lại nếu không có lý do.

---

# 12. ANIMATION & RECOMPOSITION

Animation phải phục vụ UX, không phải chỉ để tạo hiệu ứng.

Các animation như:

* staggered entrance;
* chart animation;
* count-up;
* list item animation;

chỉ nên chạy khi:

1. màn hình thực sự xuất hiện lần đầu;
2. dữ liệu/filter thực sự thay đổi;
3. hoặc animation được người dùng kích hoạt rõ ràng.

Khi navigation từ màn hình chính sang màn hình con rồi quay lại, không được tự động replay animation nếu dữ liệu không thay đổi.

Có thể sử dụng:

```text
rememberSaveable
hasAnimated
seenKeys
derived state
stable keys
```

hoặc kỹ thuật phù hợp khác.

Không bắt buộc sử dụng một implementation cụ thể nếu có giải pháp tốt hơn.

---

# 13. PERFORMANCE

Agent phải cân nhắc performance khi thay đổi code.

Đặc biệt đối với danh sách lịch sử giao dịch:

* tránh tạo object không cần thiết;
* tránh recomposition không cần thiết;
* sử dụng stable key phù hợp;
* không thực hiện expensive computation trực tiếp trong Composable nếu có thể tránh;
* không load toàn bộ dữ liệu nếu kiến trúc hiện tại hỗ trợ paging/lazy loading;
* không thực hiện database query lặp lại không cần thiết.

Không được tối ưu mù quáng.

Chỉ tối ưu khi có vấn đề thực tế hoặc có nguy cơ rõ ràng.

---

# 14. CODE QUALITY

Code mới phải:

* rõ ràng;
* dễ đọc;
* đúng responsibility;
* dễ test;
* có khả năng tái sử dụng;
* nhất quán với codebase hiện tại.

Không viết code ngắn bằng mọi giá.

Không viết abstraction phức tạp chỉ để giảm số dòng code.

Không copy-paste logic giữa các file.

Không tạo magic number/string nếu project đã có constant/resource phù hợp.

---

# 15. GIỮ NGUYÊN CODE HIỆN TẠI KHI KHÔNG CẦN THAY ĐỔI

Khi sửa một feature:

> **Chỉ thay đổi những gì cần thiết để hoàn thành yêu cầu.**

Không được tự ý:

* đổi tên hàng loạt;
* format lại toàn bộ file;
* refactor các module không liên quan;
* đổi kiến trúc;
* thay dependency;
* đổi navigation;
* đổi database schema;

nếu những thay đổi đó không cần thiết cho task.

Nếu refactor là cần thiết, phải xác định rõ phạm vi và lý do.

---

# 16. QUY TRÌNH THỰC HIỆN TASK

Mỗi task nên được xử lý theo quy trình:

```text
1. Understand
   ↓
2. Inspect existing implementation
   ↓
3. Identify reusable components / logic
   ↓
4. Evaluate the requested approach
   ↓
5. Challenge problematic assumptions
   ↓
6. Choose the smallest appropriate solution
   ↓
7. Implement
   ↓
8. Re-check architecture and duplication
   ↓
9. Validate / test
```

Sau khi implementation hoàn thành, Agent phải tự kiểm tra:

* Có tạo duplicate code không?
* Có component nào có thể reuse không?
* Có logic nào nên được đưa ra shared layer không?
* Có phá behavior cũ không?
* Có ảnh hưởng navigation/state không?
* Có ảnh hưởng dữ liệu không?
* Có tạo side effect không cần thiết không?

---

# 17. TESTING & VERIFICATION

Sau khi thay đổi code, Agent phải kiểm tra trong phạm vi có thể:

* compilation;
* lint;
* unit test;
* UI test nếu có;
* database migration nếu liên quan;
* navigation nếu liên quan.

Không được tuyên bố task hoàn thành nếu chưa kiểm tra những phần có thể kiểm tra được.

Nếu không thể chạy test hoặc build, phải nói rõ điều đó.

Không được giả định rằng code đúng chỉ vì code có vẻ hợp lệ.

---

# 18. KHÔNG TỰ Ý THAY ĐỔI DEPENDENCY

Không thêm thư viện mới nếu functionality có thể giải quyết bằng:

* thư viện hiện tại;
* Android SDK;
* Jetpack;
* component/utility đã có trong project.

Trước khi thêm dependency, phải đánh giá:

* project đã có giải pháp tương tự chưa;
* dependency có thực sự cần thiết không;
* maintenance cost;
* kích thước app;
* compatibility;
* ảnh hưởng kiến trúc.

---

# 19. COMMENT & DOCUMENTATION

Comment chỉ được thêm khi cần giải thích:

* lý do của một implementation không hiển nhiên;
* business rule;
* workaround;
* limitation;
* behavior đặc biệt.

Không viết comment chỉ để mô tả những gì code đã thể hiện rõ.

Không xóa comment hiện tại nếu comment đó vẫn còn giá trị.

---

# 20. GIT COMMIT RULES

## 20.1. Quyền hạn

AI **TUYỆT ĐỐI KHÔNG tự ý chạy `git commit`** sau khi sửa code.

Chỉ tạo commit khi người dùng yêu cầu rõ ràng.

## 20.2. Format

Commit message bắt buộc:

```text
<branch> (<type>): <message>
```

`message`:

* bắt buộc bằng English;
* ngắn gọn;
* một dòng;
* mô tả đúng thay đổi chính.

Các type:

```text
feat
fix
docs
style
refactor
chore
test
```

Ví dụ:

```text
main (feat): add expense category management
feature/expense (fix): fix transaction amount validation
feature/ui (refactor): reuse transaction card component
```

---

# 21. NHỮNG VIỆC AGENT KHÔNG ĐƯỢC TỰ Ý LÀM

Agent không được tự ý:

* tạo component khi component tương tự đã tồn tại;
* duplicate business logic;
* thay đổi kiến trúc lớn;
* thay đổi database schema không cần thiết;
* xóa dữ liệu;
* reset database để giải quyết lỗi;
* thêm dependency không cần thiết;
* thay đổi design system;
* thay đổi navigation architecture;
* refactor code không liên quan;
* chạy `git commit`;
* thay đổi behavior hiện tại mà không đánh giá tác động;
* bỏ qua validation;
* bỏ qua error/loading/empty state;
* làm mất state của người dùng khi navigation;
* replay animation không cần thiết;
* tuyên bố task đã hoàn thành khi chưa kiểm chứng.

---

# 22. NGUYÊN TẮC ƯU TIÊN CUỐI CÙNG

Khi có nhiều phương án implementation, ưu tiên theo thứ tự:

```text
1. Correctness
2. Data safety
3. Existing architecture consistency
4. Reuse
5. Maintainability
6. Simplicity
7. Performance
8. UX consistency
9. Minimal code change
```

Không đánh đổi tính chính xác của dữ liệu hoặc tính ổn định của ứng dụng chỉ để hoàn thành task nhanh hơn.

Không đánh đổi khả năng bảo trì chỉ để giảm vài dòng code.

Không tạo abstraction chỉ vì muốn code “trông chuyên nghiệp”.

Không viết code mới khi code hiện tại có thể được tận dụng.

Không thực hiện yêu cầu một cách máy móc khi Agent nhận thấy phương án đó có vấn đề.

**Mục tiêu cuối cùng của Agent không phải là viết càng nhiều code càng tốt, mà là tạo ra thay đổi đúng, nhỏ, có thể tái sử dụng, phù hợp với kiến trúc hiện tại và nâng cao chất lượng lâu dài của toàn bộ codebase.**
