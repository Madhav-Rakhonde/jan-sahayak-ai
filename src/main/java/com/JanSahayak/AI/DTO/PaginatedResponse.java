package com.JanSahayak.AI.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class PaginatedResponse<T> {
    private List<T> data;
    private boolean hasMore;
    private Long nextCursor;
    private Integer limit;
    private Integer count;

    public static <T> PaginatedResponse<T> of(List<T> data, boolean hasMore, Long nextCursor, Integer limit) {
        return PaginatedResponse.<T>builder()
                .data(data)
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .limit(limit)
                .count(data.size())
                .build();
    }
}