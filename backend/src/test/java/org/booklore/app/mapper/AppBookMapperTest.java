package org.booklore.app.mapper;

import org.booklore.app.dto.AppBookDetail;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AppBookMapperTest {

    private final AppBookMapper mapper = Mappers.getMapper(AppBookMapper.class);

    @Test
    void mapEpubProgress_prefersKoreaderProgressWhenKoreaderIsNewer() {
        Instant webReadTime = Instant.parse("2026-05-01T10:00:00Z");
        Instant koreaderSyncTime = Instant.parse("2026-05-01T11:00:00Z");

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setLastReadTime(webReadTime);
        progress.setKoreaderLastSyncTime(koreaderSyncTime);
        progress.setEpubProgressPercent(0.4f);
        progress.setKoreaderProgressPercent(0.7f);

        UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
        fileProgress.setLastReadTime(webReadTime.minusSeconds(60));
        fileProgress.setPositionData("chapter-2");
        fileProgress.setPositionHref("chapter-2.xhtml#ignored");
        fileProgress.setProgressPercent(0.4f);
        fileProgress.setContentSourceProgressPercent(0.4f);
        fileProgress.setBookFile(epubBookFile());

        AppBookDetail.EpubProgress mapped = mapper.mapEpubProgress(progress, fileProgress);

        assertThat(mapped).isNotNull();
        assertThat(mapped.getCfi()).isNull();
        assertThat(mapped.getHref()).isNull();
        assertThat(mapped.getPercentage()).isEqualTo(70.0f);
        assertThat(mapped.getLocatorPrecision()).isEqualTo("percentage_only");
        assertThat(mapped.getUpdatedAt()).isEqualTo(koreaderSyncTime);
    }

    @Test
    void mapEpubProgress_usesNewerWebReaderLocatorWhenCfiIsValid() {
        Instant webReadTime = Instant.parse("2026-05-01T12:00:00Z");
        Instant koreaderSyncTime = Instant.parse("2026-05-01T11:00:00Z");

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setLastReadTime(webReadTime);
        progress.setKoreaderLastSyncTime(koreaderSyncTime);
        progress.setEpubProgress("epubcfi(/6/2[chapter-2]!/4/2/14)");
        progress.setEpubProgressHref("chapter-2.xhtml#section-1");
        progress.setEpubProgressPercent(0.42f);
        progress.setKoreaderProgressPercent(0.7f);

        AppBookDetail.EpubProgress mapped = mapper.mapEpubProgress(progress, null);

        assertThat(mapped).isNotNull();
        assertThat(mapped.getCfi()).isEqualTo("epubcfi(/6/2[chapter-2]!/4/2/14)");
        assertThat(mapped.getHref()).isEqualTo("chapter-2.xhtml#section-1");
        assertThat(mapped.getAnchor()).isEqualTo("section-1");
        assertThat(mapped.getPercentage()).isEqualTo(0.42f);
        assertThat(mapped.getLocatorPrecision()).isEqualTo("exact");
        assertThat(mapped.getUpdatedAt()).isEqualTo(webReadTime);
    }

    @Test
    void mapEpubProgress_prefersResolvedKoreaderExactLocatorWhenProvided() {
        AppBookDetail.EpubProgress resolved = AppBookDetail.EpubProgress.builder()
                .cfi("epubcfi(/6/48!/4/2/6/1:245)")
                .href("OEBPS/Text/chapter002.xhtml")
                .anchor("chapter002")
                .contentSourceProgressPercent(42.4f)
                .bookFileId(7L)
                .locatorPrecision("exact")
                .percentage(10.2f)
                .updatedAt(Instant.parse("2026-05-06T11:43:56Z"))
                .build();

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setLastReadTime(Instant.parse("2026-05-06T11:43:56Z"));
        progress.setKoreaderLastSyncTime(Instant.parse("2026-05-06T11:43:56Z"));
        progress.setKoreaderProgressPercent(0.102f);

        AppBookDetail.EpubProgress mapped = mapper.mapEpubProgress(progress, null, resolved);

        assertThat(mapped).isSameAs(resolved);
    }

    @Test
    void mapReadProgress_prefersNewerWebReaderPercentOverKoreaderPercent() {
        Instant webReadTime = Instant.parse("2026-05-01T12:00:00Z");
        Instant koreaderSyncTime = Instant.parse("2026-05-01T11:00:00Z");

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setLastReadTime(webReadTime);
        progress.setKoreaderLastSyncTime(koreaderSyncTime);
        progress.setEpubProgressPercent(0.42f);
        progress.setKoreaderProgressPercent(0.7f);

        assertThat(mapper.mapReadProgress(progress)).isEqualTo(0.42f);
    }

    private BookFileEntity epubBookFile() {
        BookEntity book = new BookEntity();
        BookFileEntity bookFile = new BookFileEntity();
        bookFile.setId(7L);
        bookFile.setBook(book);
        bookFile.setBookType(BookFileType.EPUB);
        return bookFile;
    }
}
