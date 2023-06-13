package marsh.town.brb.BrewingStand;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.Config.Config;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeShownListener;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static marsh.town.brb.BrewingStand.PlatformPotionUtil.getFrom;
import static marsh.town.brb.BrewingStand.PlatformPotionUtil.getIngredient;

@Environment(EnvType.CLIENT)
public class BrewingRecipeBookComponent extends RecipeBookComponent implements Renderable, GuiEventListener, NarratableEntry, RecipeShownListener {
    public static final ResourceLocation RECIPE_BOOK_LOCATION = new ResourceLocation("textures/gui/recipe_book.png");
    private static final ResourceLocation BUTTON_LOCATION = new ResourceLocation("brb:textures/gui/buttons.png");
    protected BrewingStandMenu brewingStandScreenHandler;
    Minecraft client;
    private int parentWidth;
    private int parentHeight;
    private boolean narrow;
    BrewingClientRecipeBook recipeBook;
    private int leftOffset;
    protected final GhostRecipe ghostRecipe = new GhostRecipe();
    private boolean open;
    private final BrewingRecipeBookResults recipesArea = new BrewingRecipeBookResults();
    @Nullable
    private EditBox searchBox;
    private final StackedContents recipeFinder = new StackedContents();
    protected StateSwitchingButton toggleBrewableButton;
    private static final Component SEARCH_HINT;
    private final List<BrewableRecipeGroupButtonWidget> tabButtons = Lists.newArrayList();
    @Nullable
    private BrewableRecipeGroupButtonWidget currentTab;
    private boolean searching;
    protected ImageButton settingsButton;
    private String searchText;
    private static final Component ONLY_CRAFTABLES_TOOLTIP;
    private static final Component ALL_RECIPES_TOOLTIP;
    private static final Component OPEN_SETTINGS_TOOLTIP;
    boolean doubleRefresh = true;

    public BrewingRecipeBookComponent() {
        super();
    }

    public void initialize(int parentWidth, int parentHeight, Minecraft client, boolean narrow, BrewingStandMenu brewingStandScreenHandler) {
        this.client = client;
        this.minecraft = client;
        this.parentWidth = parentWidth;
        this.parentHeight = parentHeight;
        this.brewingStandScreenHandler = brewingStandScreenHandler;
        this.narrow = narrow;
        assert client.player != null;
        client.player.containerMenu = brewingStandScreenHandler;
        this.recipeBook = new BrewingClientRecipeBook();
        this.open = BetterRecipeBook.rememberedBrewingOpen;
        // this.cachedInvChangeCount = client.player.getInventory().getChangeCount();
        this.reset();

        if (BetterRecipeBook.config.keepCentered) {
            this.leftOffset = this.narrow ? 0 : 162;
        } else {
            this.leftOffset = this.narrow ? 0 : 86;
        }

        // still required?
        //client.keyboardHandler.setSendRepeatsToGui(true);
    }

    public ItemStack getInputStack(BrewableResult result) {
        Potion inputPotion = getFrom(result.recipe);
        Ingredient ingredient = getIngredient(result.recipe);
        ResourceLocation identifier = BuiltInRegistries.POTION.getKey(inputPotion);
        ItemStack inputStack;
        if (this.currentTab.getGroup() == BrewingRecipeBookGroup.BREWING_SPLASH_POTION) {
            inputStack = new ItemStack(Items.SPLASH_POTION);
        } else if (this.currentTab.getGroup() == BrewingRecipeBookGroup.BREWING_LINGERING_POTION) {
            inputStack = new ItemStack(Items.LINGERING_POTION);
        } else {
            inputStack = new ItemStack(Items.POTION);
        }

        inputStack.getOrCreateTag().putString("Potion", identifier.toString());
        return inputStack;
    }

    public void reset() {
        if (BetterRecipeBook.config.keepCentered) {
            this.leftOffset = this.narrow ? 0 : 162;
        } else {
            this.leftOffset = this.narrow ? 0 : 86;
        }

        int i = (this.parentWidth - 147) / 2 - this.leftOffset;
        int j = (this.parentHeight - 166) / 2;
        this.recipeFinder.clear();
        assert this.client.player != null;
        this.client.player.getInventory().fillStackedContents(this.recipeFinder);
        String string = this.searchBox != null ? this.searchBox.getValue() : "";
        Font var10003 = this.client.font;
        int var10004 = i + 26;
        int var10005 = j + 14;
        Objects.requireNonNull(this.client.font);
        this.searchBox = new EditBox(var10003, var10004, var10005, 79, 9 + 3, Component.translatable("itemGroup.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(true);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(16777215);
        this.searchBox.setValue(string);
        this.searchBox.setHint(SEARCH_HINT);

        this.recipesArea.initialize(this.client, i, j, brewingStandScreenHandler);
        this.tabButtons.clear();
        this.recipeBook.setFilteringCraftable(BetterRecipeBook.rememberedBrewingToggle);
        this.toggleBrewableButton = new StateSwitchingButton(i + 110, j + 12, 26, 16, this.recipeBook.isFilteringCraftable());
        this.setBookButtonTexture();

        for (BrewingRecipeBookGroup brewingRecipeBookGroup : BrewingRecipeBookGroup.getGroups()) {
            this.tabButtons.add(new BrewableRecipeGroupButtonWidget(brewingRecipeBookGroup));
        }

        if (this.currentTab != null) {
            this.currentTab = this.tabButtons.stream().filter((button) -> button.getGroup().equals(this.currentTab.getGroup())).findFirst().orElse(null);
        }

        if (this.currentTab == null) {
            this.currentTab = this.tabButtons.get(0);
        }

        if (BetterRecipeBook.config.settingsButton) {
            int u = 0;
            if (BetterRecipeBook.config.darkMode) {
                u = 18;
            }

            this.settingsButton = new ImageButton(i + 11, j + 137, 16, 18, u, 77, 19, BUTTON_LOCATION, button -> {
                Minecraft.getInstance().setScreen(AutoConfig.getConfigScreen(Config.class, Minecraft.getInstance().screen).get());
            });
        }

        this.currentTab.setStateTriggered(true);
        this.refreshResults(false);
        this.refreshTabButtons();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.open && !Objects.requireNonNull(this.client.player).isSpectator()) {
            if (this.recipesArea.mouseClicked(mouseX, mouseY, button)) {
                BrewableResult result = this.recipesArea.getLastClickedRecipe();
                if (result != null) {
                    if (this.currentTab == null) return false;
                    this.ghostRecipe.clear();

                    if (!result.hasMaterials(this.currentTab.getGroup(), brewingStandScreenHandler)) {
                        showGhostRecipe(result, brewingStandScreenHandler.slots);
                        return false;
                    }

                    ItemStack inputStack = getInputStack(result);
                    Ingredient ingredient = getIngredient(result.recipe);

                    int slotIndex = 0;
                    int usedInputSlots = 0;
                    for (Slot slot : brewingStandScreenHandler.slots) {
                        ItemStack itemStack = slot.getItem();

                        assert inputStack.getTag() != null;
                        if (inputStack.getTag().equals(itemStack.getTag()) && inputStack.getItem().equals(itemStack.getItem())) {
                            if (usedInputSlots <= 2) {
                                System.out.println(usedInputSlots);
                                assert Minecraft.getInstance().gameMode != null;
                                Minecraft.getInstance().gameMode.handleInventoryMouseClick(brewingStandScreenHandler.containerId, brewingStandScreenHandler.getSlot(slotIndex).index, 0, ClickType.PICKUP, Minecraft.getInstance().player);
                                Minecraft.getInstance().gameMode.handleInventoryMouseClick(brewingStandScreenHandler.containerId, brewingStandScreenHandler.getSlot(usedInputSlots).index, 0, ClickType.PICKUP, Minecraft.getInstance().player);
                                ++usedInputSlots;
                            }
                        } else if (ingredient.getItems()[0].getItem().equals(slot.getItem().getItem())) {
                            assert Minecraft.getInstance().gameMode != null;
                            Minecraft.getInstance().gameMode.handleInventoryMouseClick(brewingStandScreenHandler.containerId, brewingStandScreenHandler.getSlot(slotIndex).index, 0, ClickType.PICKUP, Minecraft.getInstance().player);
                            Minecraft.getInstance().gameMode.handleInventoryMouseClick(brewingStandScreenHandler.containerId, brewingStandScreenHandler.getSlot(3).index, 0, ClickType.PICKUP, Minecraft.getInstance().player);
                        }

                        ++slotIndex;
                    }

                    this.refreshResults(false);
                }

                return true;
            } else {
                assert this.searchBox != null;
                if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                } else if (this.toggleBrewableButton.mouseClicked(mouseX, mouseY, button)) {
                    boolean bl = this.toggleFilteringBrewable();
                    this.toggleBrewableButton.setStateTriggered(bl);
                    BetterRecipeBook.rememberedBrewingToggle = bl;
                    this.refreshResults(false);
                    return true;
                } else if (this.settingsButton != null) {
                    if (this.settingsButton.mouseClicked(mouseX, mouseY, button) && BetterRecipeBook.config.settingsButton) {
                        return true;
                    }
                }

                Iterator<BrewableRecipeGroupButtonWidget> var6 = this.tabButtons.iterator();

                BrewableRecipeGroupButtonWidget brewableRecipeGroupButtonWidget;
                do {
                    if (!var6.hasNext()) {
                        return false;
                    }

                    brewableRecipeGroupButtonWidget = var6.next();
                } while (!brewableRecipeGroupButtonWidget.mouseClicked(mouseX, mouseY, button));

                if (this.currentTab != brewableRecipeGroupButtonWidget) {
                    if (this.currentTab != null) {
                        this.currentTab.setStateTriggered(false);
                    }

                    this.currentTab = brewableRecipeGroupButtonWidget;
                    this.currentTab.setStateTriggered(true);
                    this.refreshResults(true);
                }
                return false;
            }
        } else {
            return false;
        }
    }

    public void showGhostRecipe(BrewableResult result, List<Slot> slots) {
        this.ghostRecipe.addIngredient(Ingredient.of(getIngredient(result.recipe).getItems()[0]), slots.get(3).x, slots.get(3).y);

        assert currentTab != null;
        ItemStack inputStack = result.inputAsItemStack(currentTab.getGroup());
        this.ghostRecipe.addIngredient(Ingredient.of(result.ingredient), slots.get(0).x, slots.get(0).y);
        this.ghostRecipe.addIngredient(Ingredient.of(inputStack), slots.get(1).x, slots.get(1).y);
        this.ghostRecipe.addIngredient(Ingredient.of(inputStack), slots.get(2).x, slots.get(2).y);
    }

    private boolean toggleFilteringBrewable() {
        boolean bl = !this.recipeBook.isFilteringCraftable();
        this.recipeBook.setFilteringCraftable(bl);
        BetterRecipeBook.rememberedBrewingToggle = bl;
        return bl;
    }

    private void refreshResults(boolean resetCurrentPage) {
        if (this.currentTab == null) return;
        if (this.searchBox == null) return;

        // Create a copy to not mess with the original list
        List<BrewableResult> results = new ArrayList<>(recipeBook.getResultsForCategory(currentTab.getGroup()));

        String string = this.searchBox.getValue();
        if (!string.isEmpty()) {
            results.removeIf(itemStack -> !itemStack.ingredient.getHoverName().getString().toLowerCase(Locale.ROOT).contains(string.toLowerCase(Locale.ROOT)));
        }

        if (this.recipeBook.isFilteringCraftable()) {
            results.removeIf((result) -> !result.hasMaterials(currentTab.getGroup(), brewingStandScreenHandler));
        }

        if (BetterRecipeBook.config.enablePinning) {
            List<BrewableResult> tempResults = Lists.newArrayList(results);

            for (BrewableResult result : tempResults) {
                if (BetterRecipeBook.pinnedRecipeManager.hasPotion(result.recipe)) {
                    results.remove(result);
                    results.add(0, result);
                }
            }
        }

         this.recipesArea.setResults(results, resetCurrentPage, currentTab.getGroup());
    }

    private void refreshTabButtons() {
        int i = (this.parentWidth - 147) / 2 - this.leftOffset - 30;
        int j = (this.parentHeight - 166) / 2 + 3;
        int l = 0;

        for (BrewableRecipeGroupButtonWidget brewableRecipeGroupButtonWidget : this.tabButtons) {
            BrewingRecipeBookGroup brewingRecipeBookGroup = brewableRecipeGroupButtonWidget.getGroup();
            if (brewingRecipeBookGroup == BrewingRecipeBookGroup.BREWING_SEARCH) {
                brewableRecipeGroupButtonWidget.visible = true;
            }
            brewableRecipeGroupButtonWidget.setPosition(i, j + 27 * l++);
        }
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (this.searchBox == null) return;
        PoseStack matrices = gui.pose();

        if (doubleRefresh) {
            // Minecraft doesn't populate the inventory on initialization so this is the only solution I have
            refreshResults(true);
            doubleRefresh = false;
        }
        if (this.isOpen()) {
            matrices.pushPose();
            matrices.translate(0.0D, 0.0D, 100.0D);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, RECIPE_BOOK_LOCATION);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int i = (this.parentWidth - 147) / 2 - this.leftOffset;
            int j = (this.parentHeight - 166) / 2;
            gui.blit(RECIPE_BOOK_LOCATION, i, j, 1, 1, 147, 166);
            this.searchBox.render(gui, mouseX, mouseY, delta);


            for (BrewableRecipeGroupButtonWidget brewableRecipeGroupButtonWidget : this.tabButtons) {
                brewableRecipeGroupButtonWidget.render(gui, mouseX, mouseY, delta);
            }

            this.toggleBrewableButton.render(gui, mouseX, mouseY, delta);

            if (BetterRecipeBook.config.settingsButton) {
                this.settingsButton.render(gui, mouseX, mouseY, delta);
            }

            this.recipesArea.draw(gui, i, j, mouseX, mouseY, delta);
            matrices.popPose();
        }
    }

    public int findLeftEdge(int width, int backgroundWidth) {
        int j;
        if (this.isOpen() && !this.narrow) {
            j = 177 + (width - backgroundWidth - 200) / 2;
        } else {
            j = (width - backgroundWidth) / 2;
        }

        return j;
    }

    public void drawGhostSlots(GuiGraphics guiGraphics, int x, int y, boolean bl, float delta) {
        this.ghostRecipe.render(guiGraphics, this.client, x, y, bl, delta);
    }

    private void setOpen(boolean opened) {
        if (opened) {
            this.reset();
        }
        this.open = opened;
    }

    public void toggleOpen() {
        this.setOpen(!this.isOpen());
    }

    protected void setBookButtonTexture() {
        this.toggleBrewableButton.initTextureValues(152, 41, 28, 18, RECIPE_BOOK_LOCATION);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return this.open ? NarrationPriority.HOVERED : NarrationPriority.NONE;
    }

    public void drawTooltip(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
        if (this.isOpen()) {
            this.recipesArea.drawTooltip(gui, mouseX, mouseY);
            if (this.toggleBrewableButton.isHoveredOrFocused()) {
                Component text = this.getCraftableButtonText();
                if (this.client.screen != null) {
                    gui.renderTooltip(Minecraft.getInstance().font, text, mouseX, mouseY);

                }
            }

            if (this.settingsButton != null) {
                if (this.settingsButton.isHoveredOrFocused() && BetterRecipeBook.config.settingsButton) {
                    if (this.client.screen != null) {
                        gui.renderTooltip(Minecraft.getInstance().font, OPEN_SETTINGS_TOOLTIP, mouseX, mouseY);
                    }
                }
            }

            this.drawGhostSlotTooltip(gui, x, y, mouseX, mouseY);
        }
    }

    private Component getCraftableButtonText() {
        return this.toggleBrewableButton.isStateTriggered() ? this.getToggleCraftableButtonText() : ALL_RECIPES_TOOLTIP;
    }

    protected Component getToggleCraftableButtonText() {
        return ONLY_CRAFTABLES_TOOLTIP;
    }

    private void drawGhostSlotTooltip(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
        ItemStack itemStack = null;

        for(int i = 0; i < this.ghostRecipe.size(); ++i) {
            GhostRecipe.GhostIngredient ghostInputSlot = this.ghostRecipe.get(i);
            int j = ghostInputSlot.getX() + x;
            int k = ghostInputSlot.getY() + y;
            if (mouseX >= j && mouseY >= k && mouseX < j + 16 && mouseY < k + 16) {
                itemStack = ghostInputSlot.getItem();
            }
        }

        if (itemStack != null && this.client.screen != null) {
            gui.renderComponentTooltip(Minecraft.getInstance().font, Screen.getTooltipFromItem(Minecraft.getInstance(), itemStack), mouseX, mouseY);
        }

    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.searching = false;
        if (this.isOpen() && !Objects.requireNonNull(this.client.player).isSpectator()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.setOpen(false);
                return true;
            } else {
                assert this.searchBox != null;
                if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                    this.refreshSearchResults();
                    return true;
                } else if (this.searchBox.isFocused() && this.searchBox.isVisible()) {
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_F) {
                    if (BetterRecipeBook.config.enablePinning) {
                        for (BrewableAnimatedResultButton resultButton : this.recipesArea.resultButtons) {
                            if (resultButton.isHoveredOrFocused()) {
                                BetterRecipeBook.pinnedRecipeManager.addOrRemoveFavouritePotion(resultButton.getRecipe().recipe);
                                this.refreshResults(false);
                                return true;
                            }
                        }
                    }
                    return false;
                } else if (this.client.options.keyChat.matches(keyCode, scanCode) && !this.searchBox.isFocused()) {
                    this.searching = true;
                    this.searchBox.setFocused(true);
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        this.searching = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char chr, int modifiers) {
        if (this.searching) {
            return false;
        } else if (this.isOpen() && !Objects.requireNonNull(this.client.player).isSpectator()) {
            assert this.searchBox != null;
            if (this.searchBox.charTyped(chr, modifiers)) {
                this.refreshSearchResults();
                return true;
            } else {
                return super.charTyped(chr, modifiers);
            }
        } else {
            return false;
        }
    }

    @Override
    public void setFocused(boolean bl) {

    }

    @Override
    public boolean isFocused() {
        return false;
    }

    private void refreshSearchResults() {
        assert this.searchBox != null;
        String string = this.searchBox.getValue().toLowerCase(Locale.ROOT);
        if (!string.equals(this.searchText)) {
            this.refreshResults(false);
            this.searchText = string;
        }

    }

    static {
        SEARCH_HINT = (Component.translatable("gui.recipebook.search_hint")).withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY);
        ONLY_CRAFTABLES_TOOLTIP = Component.translatable("brb.gui.togglePotions.brewable");
        ALL_RECIPES_TOOLTIP = Component.translatable("gui.recipebook.toggleRecipes.all");
        OPEN_SETTINGS_TOOLTIP = Component.translatable("brb.gui.settings.open");
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {

    }

    @Override
    public void recipesShown(List<Recipe<?>> list) {

    }
}
