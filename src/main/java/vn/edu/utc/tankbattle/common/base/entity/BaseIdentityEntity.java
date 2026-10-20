package vn.edu.utc.tankbattle.common.base.entity;

import jakarta.persistence.*;

/** Entity có ID số tự tăng kế thừa base này; trận/checkpoint dùng ID riêng. */
@MappedSuperclass
public abstract class BaseIdentityEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    public Long getId() {
        return id;
    }
}
