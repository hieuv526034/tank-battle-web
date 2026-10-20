package vn.edu.utc.tankbattle.common.base.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Kho CRUD cơ sở; query theo lô/aggregate vẫn viết riêng, không dùng vòng lặp findById. */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {}
