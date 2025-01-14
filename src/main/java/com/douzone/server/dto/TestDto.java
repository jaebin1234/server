package com.douzone.server.dto;


import com.douzone.server.domain.entity.TestEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestDto {
    private Long id;
    private String name;

    public TestDto(TestEntity testEntity) {
        this.id = testEntity.getId();
        this.name = testEntity.getName();

    }
}
