/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.FloatListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModDownloadPage extends Control implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final ListProperty<CurseAddon.LatestFile> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final CurseAddon addon;
    private final Profile.ProfileVersion version;
    private final DownloadCallback callback;

    public ModDownloadPage(CurseAddon addon, Profile.ProfileVersion version, @Nullable DownloadCallback callback) {
        this.addon = addon;
        this.version = version;
        this.callback = callback;

        File versionJar = StringUtils.isNotBlank(version.getVersion())
                ? version.getProfile().getRepository().getVersionJar(version.getVersion())
                : null;

        Task.runAsync(() -> {
            if (StringUtils.isNotBlank(version.getVersion())) {
                Optional<String> gameVersion = GameVersion.minecraftVersion(versionJar);
                if (gameVersion.isPresent()) {
                    List<CurseAddon.LatestFile> files = CurseModManager.getFiles(addon);
                    items.setAll(files.stream()
                            .filter(file -> file.getGameVersion().contains(gameVersion.get()))
                            .sorted(Comparator.comparing(CurseAddon.LatestFile::getParsedFileDate).reversed())
                            .collect(Collectors.toList()));
                    return;
                }
            }
            List<CurseAddon.LatestFile> files = CurseModManager.getFiles(addon);
            files.sort(Comparator.comparing(CurseAddon.LatestFile::getParsedFileDate).reversed());
            items.setAll(files);
        }).start();

        this.state.set(State.fromTitle(addon.getName()));
    }

    public CurseAddon getAddon() {
        return addon;
    }

    public Profile.ProfileVersion getVersion() {
        return version;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public boolean isFailed() {
        return failed.get();
    }

    public BooleanProperty failedProperty() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed.set(failed);
    }

    public void download(CurseAddon.LatestFile file) {
        if (this.callback == null) {
            saveAs(file);
        } else {
            this.callback.download(version.getProfile(), version.getVersion(), file);
        }
    }

    public void saveAs(CurseAddon.LatestFile file) {
        String extension = StringUtils.substringAfterLast(file.getFileName(), '.');

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("button.save_as"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("file"), "*." + extension));
        fileChooser.setInitialFileName(file.getFileName());
        File dest = fileChooser.showSaveDialog(Controllers.getStage());
        if (dest == null) {
            return;
        }

        Controllers.taskDialog(
                new FileDownloadTask(NetworkUtils.toURL(file.getDownloadUrl()), dest).executor(true),
                i18n("message.downloading")
        );
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadPageSkin(this);
    }

    private static class ModDownloadPageSkin extends SkinBase<ModDownloadPage> {

        protected ModDownloadPageSkin(ModDownloadPage control) {
            super(control);

            BorderPane pane = new BorderPane();

            HBox descriptionPane = new HBox(8);
            descriptionPane.setAlignment(Pos.CENTER);
            pane.setTop(descriptionPane);
            descriptionPane.getStyleClass().add("card");
            BorderPane.setMargin(descriptionPane, new Insets(11, 11, 0, 11));

            TwoLineListItem content = new TwoLineListItem();
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setTitle(getSkinnable().addon.getName());
            content.setSubtitle(getSkinnable().addon.getSummary());
            content.getTags().setAll(getSkinnable().addon.getCategories().stream()
                    .map(category -> i18n("curse.category." + category.getCategoryId()))
                    .collect(Collectors.toList()));

            ImageView imageView = new ImageView();
            for (CurseAddon.Attachment attachment : getSkinnable().addon.getAttachments()) {
                if (attachment.isDefault()) {
                    imageView.setImage(new Image(attachment.getThumbnailUrl(), 40, 40, true, true, true));
                }
            }

            JFXButton openUrlButton = new JFXButton();
            openUrlButton.getStyleClass().add("toggle-icon4");
            openUrlButton.setGraphic(SVG.launchOutline(Theme.blackFillBinding(), -1, -1));
            openUrlButton.setOnAction(e -> FXUtils.openLink(getSkinnable().addon.getWebsiteUrl()));

            descriptionPane.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content, openUrlButton);


            SpinnerPane spinnerPane = new SpinnerPane();
            pane.setCenter(spinnerPane);
            {
                spinnerPane.loadingProperty().bind(getSkinnable().loadingProperty());
                spinnerPane.failedReasonProperty().bind(Bindings.createStringBinding(() -> {
                    if (getSkinnable().isFailed()) {
                        return i18n("download.failed.refresh");
                    } else {
                        return null;
                    }
                }, getSkinnable().failedProperty()));

                JFXListView<CurseAddon.LatestFile> listView = new JFXListView<>();
                spinnerPane.setContent(listView);
                Bindings.bindContent(listView.getItems(), getSkinnable().items);
                listView.setCellFactory(x -> new FloatListCell<CurseAddon.LatestFile>(listView) {
                    TwoLineListItem content = new TwoLineListItem();
                    StackPane graphicPane = new StackPane();
                    JFXButton saveAsButton = new JFXButton();

                    {
                        HBox container = new HBox(8);
                        container.setAlignment(Pos.CENTER_LEFT);
                        pane.getChildren().add(container);

                        saveAsButton.getStyleClass().add("toggle-icon4");
                        saveAsButton.setGraphic(SVG.contentSaveMoveOutline(Theme.blackFillBinding(), -1, -1));

                        HBox.setHgrow(content, Priority.ALWAYS);
                        container.getChildren().setAll(graphicPane, content, saveAsButton);
                    }

                    @Override
                    protected void updateControl(CurseAddon.LatestFile dataItem, boolean empty) {
                        if (empty) return;
                        content.setTitle(dataItem.getDisplayName());
                        content.setSubtitle(FORMATTER.format(dataItem.getParsedFileDate()));
                        content.getTags().setAll(dataItem.getGameVersion());
                        saveAsButton.setOnMouseClicked(e -> getSkinnable().saveAs(dataItem));

                        switch (dataItem.getReleaseType()) {
                            case 1: // release
                                graphicPane.getChildren().setAll(SVG.releaseCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.release"));
                                break;
                            case 2: // beta
                                graphicPane.getChildren().setAll(SVG.betaCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.snapshot"));
                                break;
                            case 3: // alpha
                                graphicPane.getChildren().setAll(SVG.alphaCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.snapshot"));
                                break;
                        }
                    }
                });

                listView.setOnMouseClicked(e -> {
                    if (listView.getSelectionModel().getSelectedIndex() < 0)
                        return;
                    CurseAddon.LatestFile selectedItem = listView.getSelectionModel().getSelectedItem();
                    getSkinnable().download(selectedItem);
                });
            }

            getChildren().setAll(pane);
        }
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    public interface Project {

    }

    public interface ProjectVersion {

    }

    public interface DownloadSource {

    }

    public interface DownloadCallback {
        void download(Profile profile, @Nullable String version, CurseAddon.LatestFile file);
    }
}
