package vn.edu.utc.tankbattle.common.dto.request;

import jakarta.validation.constraints.*;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;

/** Giới hạn phân trang ở một nơi, tránh yêu cầu size quá lớn làm nặng database. */
public class PageRequest extends BaseRequest {
    @Min(value = 0, message = "{page.invalid}")
    @Max(value = 10000, message = "{page.invalid}")
    private int page = 0;

    @Min(value = 1, message = "{page.invalid}")
    @Max(value = 50, message = "{page.invalid}")
    private int size = 20;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int offset() {
        return Math.multiplyExact(page, size);
    }
}
