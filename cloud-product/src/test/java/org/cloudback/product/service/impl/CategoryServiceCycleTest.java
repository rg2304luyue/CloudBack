package org.cloudback.product.service.impl;

import org.cloudback.common.exception.BusinessException;
import org.cloudback.product.mapper.CategoryMapper;
import org.cloudback.product.model.entity.Category;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 分类父链环校验与递归兜底：一次 PUT 不能让公开接口栈溢出。 */
class CategoryServiceCycleTest {

    private final CategoryMapper mapper = mock(CategoryMapper.class);
    private final CategoryServiceImpl service = new CategoryServiceImpl(mapper);

    @Test
    void movingCategoryUnderItselfIsRejected() {
        when(mapper.selectById(1L)).thenReturn(category(1L, 0L));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateCategory("ADMIN", category(1L, 1L)));
        assertTrue(error.getMessage().contains("父分类不能是自己"));
        verify(mapper, never()).updateById(any(Category.class));
    }

    @Test
    void movingCategoryUnderItsOwnDescendantIsRejected() {
        when(mapper.selectById(1L)).thenReturn(category(1L, 0L));
        when(mapper.selectById(3L)).thenReturn(category(3L, 2L));
        // 1 → 2 → 3：把 1 挂到 3 下面会成环
        when(mapper.selectList(any())).thenReturn(List.of(category(1L, 0L), category(2L, 1L), category(3L, 2L)));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateCategory("ADMIN", category(1L, 3L)));
        assertTrue(error.getMessage().contains("父分类不能是自己的子分类"));
        verify(mapper, never()).updateById(any(Category.class));
    }

    @Test
    void legalMoveIsPersisted() {
        when(mapper.selectById(1L)).thenReturn(category(1L, 0L));
        when(mapper.selectById(9L)).thenReturn(category(9L, 0L));
        when(mapper.selectList(any())).thenReturn(List.of(category(1L, 0L), category(9L, 0L)));
        service.updateCategory("ADMIN", category(1L, 9L));
        verify(mapper).updateById(any(Category.class));
    }

    @Test
    void treeBuildingSurvivesExistingCycleInDatabase() {
        // 历史脏数据：2 的父是 1，1 的父是 2
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(List.of(category(1L, 2L), category(2L, 1L))));
        assertDoesNotThrow(() -> service.getCategoryTree());
        assertDoesNotThrow(() -> service.collectDescendantIds(1L));
    }

    private Category category(Long id, Long parentId) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        category.setName("c" + id);
        return category;
    }
}