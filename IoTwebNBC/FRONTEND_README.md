# 🌐 IoT Sensor Dashboard - Frontend JSP

## 📋 Mô tả
Frontend JSP hiển thị dữ liệu sensor theo thời gian thực với giao diện đẹp và responsive.

## ✨ Tính năng chính

### 🎯 **Dashboard chính**
- **Stats Cards**: Hiển thị giá trị hiện tại của Temperature, Humidity, Light Level và Status
- **Data Table**: Bảng 10 dòng dữ liệu sensor mới nhất
- **Real-time Updates**: Tự động cập nhật mỗi 2 giây
- **Room Selector**: Chuyển đổi giữa các phòng (room1, room2, room3)

### 🎨 **Giao diện**
- **Modern Design**: Gradient backgrounds, card layouts, responsive design
- **Color Coding**: Màu sắc khác nhau cho từng loại dữ liệu
- **Mobile Friendly**: Responsive design cho mobile và tablet
- **Loading States**: Spinner và loading indicators

### ⚡ **Performance**
- **Auto-refresh**: Cập nhật dữ liệu mỗi 2 giây
- **Smart Refresh**: Tạm dừng refresh khi tab không active
- **Error Handling**: Xử lý lỗi và hiển thị thông báo

## 🚀 Cách sử dụng

### 1. **Truy cập Dashboard**
```
http://localhost:8080/
```
Tự động redirect đến: `http://localhost:8080/dashboard/room1`

### 2. **Chuyển đổi phòng**
- Sử dụng dropdown selector ở giữa trang
- Chọn room1, room2, hoặc room3
- URL sẽ thay đổi tương ứng

### 3. **Xem dữ liệu**
- **Stats Cards**: Giá trị hiện tại ở đầu trang
- **Data Table**: 10 bản ghi mới nhất ở dưới
- **Auto-refresh**: Dữ liệu tự động cập nhật

## 🛠️ Cấu trúc file

```
src/main/webapp/
├── WEB-INF/
│   ├── views/
│   │   └── dashboard.jsp          # Dashboard chính
│   └── web.xml                    # Cấu hình web app
├── index.jsp                      # Trang chủ (redirect)
└── FRONTEND_README.md             # Hướng dẫn này

src/main/java/
└── com/example/IoTwebNBC/
    └── controller/
        └── WebController.java     # Controller xử lý JSP
```

## 📱 Responsive Design

### **Desktop (>768px)**
- Grid layout 4 cột cho stats cards
- Bảng đầy đủ với padding lớn
- Header lớn với gradient

### **Mobile (≤768px)**
- Grid layout 1 cột cho stats cards
- Bảng compact với padding nhỏ
- Font size điều chỉnh cho mobile

## 🔧 Cấu hình

### **Dependencies (pom.xml)**
```xml
<!-- JSP Support -->
<dependency>
    <groupId>org.apache.tomcat.embed</groupId>
    <artifactId>tomcat-embed-jasper</artifactId>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>jakarta.servlet.jsp.jstl</groupId>
    <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
</dependency>
```

### **Auto-refresh Settings**
```javascript
// Refresh mỗi 2 giây
refreshInterval = setInterval(loadSensorData, 2000);

// Tạm dừng khi tab không active
document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
        stopAutoRefresh();
    } else {
        startAutoRefresh();
    }
});
```

## 🎨 Customization

### **Thay đổi màu sắc**
```css
/* Header gradient */
.header {
    background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

/* Stats cards gradient */
.stat-card {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
```

### **Thay đổi refresh interval**
```javascript
// Thay đổi từ 2 giây thành 5 giây
refreshInterval = setInterval(loadSensorData, 5000);
```

### **Thêm phòng mới**
```html
<select id="roomSelector" onchange="changeRoom()">
    <option value="room1">Room 1</option>
    <option value="room2">Room 2</option>
    <option value="room3">Room 3</option>
    <option value="room4">Room 4</option>  <!-- Thêm phòng mới -->
</select>
```

## 🐛 Troubleshooting

### **JSP không hiển thị**
1. Kiểm tra dependencies trong `pom.xml`
2. Restart Spring Boot application
3. Kiểm tra console logs

### **Dữ liệu không cập nhật**
1. Kiểm tra API endpoint `/api/data_sensor/{room}/by-id`
2. Kiểm tra browser console cho JavaScript errors
3. Kiểm tra network tab trong DevTools

### **Giao diện bị lỗi**
1. Kiểm tra CSS có load đúng không
2. Clear browser cache
3. Kiểm tra responsive design

## 📊 API Endpoints sử dụng

- `GET /api/data_sensor/{room}/by-id` - Lấy dữ liệu theo ID (mới nhất)
- `GET /api/data_sensor/{room}/by-timestamp` - Lấy dữ liệu theo timestamp
- `GET /api/data_sensor/{room}/debug` - Debug info

## 🔄 Workflow

1. **User truy cập** `/` → Redirect đến `/dashboard/room1`
2. **Dashboard load** → Gọi API lấy dữ liệu
3. **Update UI** → Hiển thị stats cards và data table
4. **Auto-refresh** → Cập nhật mỗi 2 giây
5. **User chuyển phòng** → Load dữ liệu phòng mới

## 🎯 Kết quả mong đợi

- ✅ Dashboard đẹp, responsive
- ✅ Dữ liệu cập nhật theo thời gian thực
- ✅ Chuyển đổi phòng dễ dàng
- ✅ Hiển thị 10 dòng dữ liệu mới nhất
- ✅ Stats cards với giá trị hiện tại
- ✅ Auto-refresh mỗi 2 giây
- ✅ Error handling và loading states
