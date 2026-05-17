package org.cloudback.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类，所有数据库实体继承此类。
 * 提供通用字段: id(雪花算法)、createTime、updateTime、逻辑删除标记。
 * 字段值由 {@link AutoFillMetaObjectHandler} 自动填充。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，使用雪花算法自动生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
