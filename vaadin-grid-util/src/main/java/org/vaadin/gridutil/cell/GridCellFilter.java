package org.vaadin.gridutil.cell;

import com.vaadin.data.BeanPropertySet;
import com.vaadin.data.PropertyDefinition;
import com.vaadin.data.PropertySet;
import com.vaadin.data.ValueProvider;
import com.vaadin.data.provider.InMemoryDataProviderHelpers;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.FontIcon;
import com.vaadin.server.SerializablePredicate;
import com.vaadin.server.Sizeable.Unit;
import com.vaadin.shared.ui.ValueChangeMode;
import com.vaadin.ui.*;
import com.vaadin.ui.components.grid.HeaderRow;
import com.vaadin.ui.themes.ValoTheme;
import org.vaadin.gridutil.cell.filter.EqualFilter;
import org.vaadin.gridutil.cell.filter.SimpleStringFilter;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;


/**
 * GridCellFilter helper that has a bunch of different filtering types
 *
 * @author Marten Prieß (http://www.rocketbase.io)
 * @version 1.1
 */
public class GridCellFilter<T> implements Serializable {

    public static String STYLENAME_GRIDCELLFILTER = "gridcellfilter";

    private Grid grid;

    private ListDataProvider<T> dataProvider;

    private HeaderRow filterHeaderRow;

    private Map<CellFilterId, CellFilterComponent> cellFilters;

    private Map<CellFilterId, SerializablePredicate> assignedFilters;

    private boolean visible = true;

    private List<CellFilterChangedListener> cellFilterChangedListeners;

    private BeanPropertySet<T> propertySet;

    /**
     * keeps link to Grid and added HeaderRow<br>
     * afterwards you need to set filter specification for each row<br>
     * please take care that your Container implements Filterable!
     *
     * @param grid that should get added a HeaderRow that this component will manage
     */
    public GridCellFilter(Grid<T> grid, Class<T> beanType) {
        this.grid = grid;
        filterHeaderRow = grid.appendHeaderRow();
        cellFilters = new HashMap<>();
        assignedFilters = new HashMap<>();
        cellFilterChangedListeners = new ArrayList<>();


        if (!(grid.getDataProvider() instanceof ListDataProvider)) {
            throw new RuntimeException("works only with ListDataProvider");
        } else {
            dataProvider = (ListDataProvider<T>) grid.getDataProvider();
            propertySet = (BeanPropertySet<T>) BeanPropertySet.get(beanType);
        }
    }

    /**
     * generated HeaderRow
     *
     * @return added HeaderRow during intialization
     */
    public HeaderRow getFilterRow() {
        return filterHeaderRow;
    }

    /**
     * get list of filtered ColumnIds
     *
     * @return id of all properties that are currently filtered
     */
    public Set<String> filteredColumnIds() {
        return assignedFilters.keySet()
                              .stream()
                              .map(cellFilterId -> cellFilterId.getColumnId())
                              .collect(Collectors.toSet());
    }

    /**
     * add a listener for filter changes
     *
     * @param listener that should get triggered on changes
     */
    public void addCellFilterChangedListener(CellFilterChangedListener listener) {
        cellFilterChangedListeners.add(listener);
    }

    /**
     * remove a listener for filter changes
     *
     * @param listener that should get removed
     *
     * @return true when found and removed
     */
    public boolean removeCellFilterChangedListener(CellFilterChangedListener listener) {
        return cellFilterChangedListeners.remove(listener);
    }

    /**
     * notify all registered listeners
     */
    protected void notifyCellFilterChanged() {
        for (CellFilterChangedListener listener : cellFilterChangedListeners) {
            listener.changedFilter(this);
        }
    }

    /**
     * will remove or add the filterHeaderRow<br>
     * The grid itself has no feature for changing the visibility of a headerRow
     *
     * @param visibile should get displayed?
     */
    public void setVisible(boolean visibile) {
        if (visible != visibile) {
            if (visibile) {
                filterHeaderRow = grid.appendHeaderRow();

                for (Entry<CellFilterId, CellFilterComponent> entry : cellFilters.entrySet()) {
                    handleFilterRow(entry.getKey(), entry.getValue());
                }
            } else {
                clearAllFilters();
                for (Entry<CellFilterId, CellFilterComponent> entry : cellFilters.entrySet()) {
                    if (null != filterHeaderRow.getCell(entry.getKey().getColumnId())) {
                        filterHeaderRow.getCell(entry.getKey().getColumnId()).setText("");
                    }
                }
                grid.removeHeaderRow(filterHeaderRow);
            }
            visible = visibile;
        }
    }

    /**
     * get filter by columnId
     *
     * @param columnId id of property
     *
     * @return CellFilterComponent
     */
    public CellFilterComponent getCellFilter(String columnId) {
        return cellFilters.get(columnId);
    }

    /**
     * removes all filters and clear all inputs
     */
    public void clearAllFilters() {
        for (Entry<CellFilterId, CellFilterComponent> entry : cellFilters.entrySet()) {
            entry.getValue().clearFilter();
            removeFilter(entry.getKey(), false);
        }
        notifyCellFilterChanged();
    }

    /**
     * clear's a specific filter by columnId
     *
     * @param columnId id of property
     */
    public void clearFilter(String columnId) {
        Set<Entry<CellFilterId, CellFilterComponent>> foundEntries = cellFilters.entrySet()
                                                                                .stream()
                                                                                .filter(entry -> entry.getKey()
                                                                                                      .getColumnId()
                                                                                                      .equals(columnId))
                                                                                .collect(Collectors.toSet());
        foundEntries.forEach(entry -> {
            entry.getValue().clearFilter();
            removeFilter(entry.getKey());
        });
    }

    /**
     * link component to headerRow and take care for styling
     *
     * @param cellFilterId id information
     * @param cellFilter   component will get added to filterRow
     */
    protected void handleFilterRow(final CellFilterId cellFilterId, CellFilterComponent<?> cellFilter) {
        cellFilters.put(cellFilterId, cellFilter);
        cellFilter.getComponent().setWidth(100, Unit.PERCENTAGE);
        final String columnId = cellFilterId.getColumnId();
        if (null != filterHeaderRow.getCell(columnId)) {
            filterHeaderRow.getCell(columnId).setComponent(cellFilter.getComponent());
            filterHeaderRow.getCell(columnId).setStyleName("filter-header");
        }
    }

    /**
     * checks assignedFilters replace already handled one and add new one
     *
     * @param filter       container filter
     * @param cellFilterId id information
     */
    public void replaceFilter(SerializablePredicate filter, CellFilterId cellFilterId) {
        assignedFilters.put(cellFilterId, filter);
        refreshFilters();
    }

    private void refreshFilters() {
        dataProvider.clearFilters();
        SerializablePredicate<T> filter = null;
        for (Entry<CellFilterId, SerializablePredicate> entry : assignedFilters.entrySet()) {
            final CellFilterId cellFilterId = entry.getKey();
            SerializablePredicate<T> singleFilter = InMemoryDataProviderHelpers.createValueProviderFilter
                    (cellFilterId.getGetter(),
                                                                                                          entry.getValue());
            if (filter == null) {
                filter = singleFilter;
            } else {
                SerializablePredicate<T> tempFilter = filter;
                filter = (item -> tempFilter.test(item) && singleFilter.test(item));
            }
        }
        if (filter != null) {
            dataProvider.setFilter(filter);
        }
    }

    /**
     * remove the filter and notify listeners
     *
     * @param cellFilterId id of property
     */
    public void removeFilter(CellFilterId cellFilterId) {
        removeFilter(cellFilterId, true);
    }

    protected void removeFilter(CellFilterId cellFilterId, boolean notify) {
        if (assignedFilters.containsKey(cellFilterId)) {
            assignedFilters.remove(cellFilterId);
            refreshFilters();
            if (notify) {
                notifyCellFilterChanged();
            }
        }
    }

    /**
     * allows to create a {@link CellFilterId} with only a columnId.<br>
     * Needed to set a custom filter using  {@link #setCustomFilter(CellFilterId, CellFilterComponent)}
     *
     * @param columnId id of column and property if equal
     *
     * @return the {@link CellFilterId}
     */
    public CellFilterId createCellFilterId(final String columnId) {
        return new CellFilterId(propertySet, columnId);
    }

    /**
     * allows to create a {@link CellFilterId} for the given information<br>
     * Needed to set a custom filter using  {@link #setCustomFilter(CellFilterId, CellFilterComponent)}
     *
     * @param columnId   id of column
     * @param propertyId id of property
     *
     * @return the {@link CellFilterId}
     */
    public CellFilterId createCellFilterId(final String columnId, final String propertyId) {
        return new CellFilterId(propertySet, columnId, propertyId);
    }

    /**
     * allows to add custom FilterComponents to the GridCellFilter
     *
     * @param cellFilterId the id created with {@link #createCellFilterId(String)} or
     * {@link #createCellFilterId(String, *                     String)}
     * @param component    that implements the interface
     *
     * @return your created component that is linked with the GridCellFilter
     */
    public CellFilterComponent setCustomFilter(final CellFilterId cellFilterId, CellFilterComponent component) {
        handleFilterRow(cellFilterId, component);
        return component;
    }

    /**
     * assign a <b>SimpleStringFilter</b> to grid for given columnId<br>
     * could also be used for NumberField when you would like to do filter by startWith for example
     *
     * @param columnId        id of property
     * @param ignoreCase      property of SimpleStringFilter
     * @param onlyMatchPrefix property of SimpleStringFilter
     *
     * @return CellFilterComponent that contains TextField
     */
    public CellFilterComponent<TextField> setTextFilter(String columnId, boolean ignoreCase, boolean onlyMatchPrefix) {
        return setTextFilter(columnId, ignoreCase, onlyMatchPrefix, null);
    }

    /**
     * assign a <b>SimpleStringFilter</b> to grid for given columnId<br>
     * could also be used for NumberField when you would like to do filter by startWith for example
     *
     * @param columnId        id of column and property if equal
     * @param ignoreCase      property of SimpleStringFilter
     * @param onlyMatchPrefix property of SimpleStringFilter
     * @param inputPrompt     hint for user
     *
     * @return CellFilterComponent that contains TextField
     */
    public CellFilterComponent<TextField> setTextFilter(String columnId,
                                                        boolean ignoreCase,
                                                        boolean onlyMatchPrefix,
                                                        String inputPrompt) {
        return setTextFilter(columnId, columnId, ignoreCase, onlyMatchPrefix, inputPrompt);
    }

    /**
     * assign a <b>SimpleStringFilter</b> to grid for given columnId<br>
     * could also be used for NumberField when you would like to do filter by startWith for example
     *
     * @param columnId        id of column
     * @param propertyId      id of property
     * @param ignoreCase      property of SimpleStringFilter
     * @param onlyMatchPrefix property of SimpleStringFilter
     * @param inputPrompt     hint for user
     *
     * @return CellFilterComponent that contains TextField
     */
    public CellFilterComponent<TextField> setTextFilter(String columnId,
                                                        String propertyId,
                                                        boolean ignoreCase,
                                                        boolean onlyMatchPrefix,
                                                        String inputPrompt) {
        final CellFilterId cellFilterId = new CellFilterId(propertySet, columnId, propertyId);
        CellFilterComponent<TextField> filter = new CellFilterComponent<TextField>() {

            TextField textField = new TextField();

            String currentValue = "";

            public void triggerUpdate() {
                if (currentValue == null || currentValue.isEmpty()) {
                    removeFilter(cellFilterId);
                } else {
                    replaceFilter(new SimpleStringFilter(currentValue, ignoreCase, onlyMatchPrefix), cellFilterId);
                }
            }

            @Override
            public TextField layoutComponent() {
                textField.setPlaceholder(inputPrompt);
                textField.addStyleName(STYLENAME_GRIDCELLFILTER);
                textField.addStyleName(ValoTheme.TEXTFIELD_TINY);
                textField.setValueChangeTimeout(200);
                textField.setValueChangeMode(ValueChangeMode.TIMEOUT);
                // used to allow changes from outside
                textField.addValueChangeListener(e -> {
                    currentValue = textField.getValue();
                    triggerUpdate();
                });
                return textField;
            }

            @Override
            public void clearFilter() {
                textField.clear();
            }
        };
        handleFilterRow(cellFilterId, filter);
        return filter;
    }

    /**
     * assign a <b>EqualFilter</b> to grid for given columnId
     *
     * @param columnId id of column and property if equal
     * @param beanType class of selection
     * @param beans    selection for ComboBox
     *
     * @return CellFilterComponent that contains ComboBox
     */
    public <B> CellFilterComponent<ComboBox<B>> setComboBoxFilter(String columnId, Class<B> beanType, List<B> beans) {
        return setComboBoxFilter(columnId, columnId, beanType, beans);
    }

    /**
     * assign a <b>EqualFilter</b> to grid for given columnId
     *
     * @param columnId   id of column
     * @param propertyId id of property
     * @param beanType   class of selection
     * @param beans      selection for ComboBox
     *
     * @return CellFilterComponent that contains ComboBox
     */
    public <B> CellFilterComponent<ComboBox<B>> setComboBoxFilter(String columnId,
                                                                  String propertyId,
                                                                  Class<B> beanType,
                                                                  List<B> beans) {
        final CellFilterId cellFilterId = new CellFilterId(propertySet, columnId, propertyId);
        CellFilterComponent<ComboBox<B>> filter = new CellFilterComponent<ComboBox<B>>() {

            ComboBox<B> comboBox = new ComboBox();

            public void triggerUpdate() {
                if (comboBox.getValue() != null) {
                    replaceFilter(new EqualFilter(comboBox.getValue()), cellFilterId);
                } else {
                    removeFilter(cellFilterId);
                }
            }

            @Override
            public ComboBox<B> layoutComponent() {
                comboBox.setEmptySelectionAllowed(true);
                comboBox.addStyleName(STYLENAME_GRIDCELLFILTER);
                comboBox.addStyleName(ValoTheme.COMBOBOX_TINY);
                comboBox.setItems(beans);
                comboBox.addValueChangeListener(e -> triggerUpdate());
                return comboBox;
            }

            @Override
            public void clearFilter() {
                comboBox.setValue(null);
            }
        };

        handleFilterRow(cellFilterId, filter);
        return filter;
    }

    /**
     * assign a <b>EqualFilter</b> to grid for given columnId
     *
     * @param columnId id of property
     *
     * @return drawn comboBox in order to add some custom styles
     */
    public ComboBox setBooleanFilter(String columnId) {
        return setBooleanFilter(columnId, BooleanRepresentation.TRUE_VALUE, BooleanRepresentation.FALSE_VALUE);
    }

    /**
     * assign a <b>EqualFilter</b> to grid for given columnId
     *
     * @param columnId            id of property and column if equal
     * @param trueRepresentation  specify caption and icon
     * @param falseRepresentation specify caption and icon
     *
     * @return drawn comboBox in order to add some custom styles
     */
    public ComboBox setBooleanFilter(String columnId,
                                     BooleanRepresentation trueRepresentation,
                                     BooleanRepresentation falseRepresentation) {
        return setBooleanFilter(columnId, columnId, trueRepresentation, falseRepresentation);
    }

    /**
     * assign a <b>EqualFilter</b> to grid for given columnId
     *
     * @param columnId            id of column
     * @param propertyId          id of property
     * @param trueRepresentation  specify caption and icon
     * @param falseRepresentation specify caption and icon
     *
     * @return drawn comboBox in order to add some custom styles
     */
    public ComboBox setBooleanFilter(String columnId,
                                     String propertyId,
                                     BooleanRepresentation trueRepresentation,
                                     BooleanRepresentation falseRepresentation) {
        final CellFilterId cellFilterId = new CellFilterId(propertySet, columnId, propertyId);
        CellFilterComponent<ComboBox<BooleanRepresentation>> filter = new
                CellFilterComponent<ComboBox<BooleanRepresentation>>() {

            ComboBox<BooleanRepresentation> comboBox = new ComboBox();

            public void triggerUpdate() {
                if (comboBox.getValue() != null) {
                    replaceFilter(new EqualFilter(comboBox.getValue().getValue()), cellFilterId);
                } else {
                    removeFilter(cellFilterId);
                }
            }

            @Override
            public ComboBox<BooleanRepresentation> layoutComponent() {

                comboBox.setItemIconGenerator(BooleanRepresentation::getIcon);
                comboBox.setItemCaptionGenerator(BooleanRepresentation::getCaption);
                comboBox.setItems(Arrays.asList(trueRepresentation, falseRepresentation));

                comboBox.setEmptySelectionAllowed(true);
                comboBox.addStyleName(STYLENAME_GRIDCELLFILTER);
                comboBox.addStyleName(ValoTheme.COMBOBOX_TINY);
                comboBox.addValueChangeListener(e -> triggerUpdate());
                return comboBox;
            }

            @Override
            public void clearFilter() {
                comboBox.setValue(null);
            }
        };

        handleFilterRow(cellFilterId, filter);
        return filter.getComponent();
    }

    /**
     * assign a <b>BetweenFilter</b> to grid for given columnId<br>
     * only supports type of <b>Integer, Double, Float, BigInteger and BigDecimal</b>
     *
     * @param columnId id of property and column if equal
     * @param type     type of the property
     *
     * @return RangeCellFilterComponent that holds both TextFields (smallest and biggest as propertyId) and FilterGroup
     */
    public <T extends Number & Comparable<? super T>> RangeCellFilterComponentTyped<T, TextField, HorizontalLayout>
    setNumberFilter(
            String columnId,
            Class type) {
        return setNumberFilter(columnId, columnId, type);
    }

    /**
     * assign a <b>BetweenFilter</b> to grid for given columnId<br>
     * only supports type of <b>Integer, Double, Float, BigInteger and BigDecimal</b>
     *
     * @param columnId   id of column
     * @param propertyId id of property
     * @param type       type of the property
     *
     * @return RangeCellFilterComponent that holds both TextFields (smallest and biggest as propertyId) and FilterGroup
     */
    public <T extends Number & Comparable<? super T>> RangeCellFilterComponentTyped<T, TextField, HorizontalLayout>
    setNumberFilter(
            String columnId,
            String propertyId,
            Class type) {
        return setNumberFilter(columnId,
                               propertyId,
                               type,
                               String.format("couldn't convert to %s", type.getSimpleName()),
                               null,
                               null);
    }

    /**
     * assign a <b>BetweenFilter</b> to grid for given columnId<br>
     * only supports type of <b>Integer, Double, Float, BigInteger and BigDecimal</b>
     *
     * @param columnId              id of property and column if equal
     * @param type                  type of the property
     * @param converterErrorMessage message to be displayed in case of a converter error
     * @param smallestInputPrompt   hint for user
     * @param biggestInputPrompt    hint for user
     *
     * @return RangeCellFilterComponent that holds both TextFields (smallest and biggest as propertyId) and FilterGroup
     */
    public <T extends Number & Comparable<? super T>> RangeCellFilterComponentTyped<T, TextField, HorizontalLayout>
    setNumberFilter(
            String columnId,
            Class<T> type,
            String converterErrorMessage,
            String smallestInputPrompt,
            String biggestInputPrompt) {
        return setNumberFilter(columnId,
                               columnId,
                               type,
                               converterErrorMessage,
                               smallestInputPrompt,
                               biggestInputPrompt);
    }

    /**
     * assign a <b>BetweenFilter</b> to grid for given columnId<br>
     * only supports type of <b>Integer, Double, Float, BigInteger and BigDecimal</b>
     *
     * @param columnId              id of column
     * @param propertyId            id of property
     * @param type                  type of the property
     * @param converterErrorMessage message to be displayed in case of a converter error
     * @param smallestInputPrompt   hint for user
     * @param biggestInputPrompt    hint for user
     *
     * @return RangeCellFilterComponent that holds both TextFields (smallest and biggest as propertyId) and FilterGroup
     */
    public <T extends Number & Comparable<? super T>> RangeCellFilterComponentTyped<T, TextField, HorizontalLayout>
    setNumberFilter(
            String columnId,
            String propertyId,
            Class<T> type,
            String converterErrorMessage,
            String smallestInputPrompt,
            String biggestInputPrompt) {
        final CellFilterId cellFilterId = new CellFilterId(propertySet, columnId, propertyId);
        final RangeCellFilterComponentTyped<T, TextField, HorizontalLayout> filter = RangeCellFilterComponentFactory
                .createForNumberType(
                cellFilterId,
                type,
                converterErrorMessage,
                smallestInputPrompt,
                biggestInputPrompt,
                (serFilter, cellFilterId1) -> this.replaceFilter(serFilter, cellFilterId1),
                this::removeFilter);

        handleFilterRow(cellFilterId, filter);
        return filter;
    }

    /**
     * assign a <b>BetweenFilter</b> to grid for given columnId<br>
     *
     * @param columnId id of property and column if equal
     *
     * @return RangeCellFilterComponent that holds both DateFields (smallest and biggest as propertyId) and FilterGroup
     */
    public RangeCellFilterComponent<DateField, HorizontalLayout> setDateFilter(String columnId) {
        return setDateFilter(columnId, columnId);
    }

    /**
     * assign a <b>BetweenFilter</b> to grid for given columnId and propertyId<br>
     *
     * @param columnId   id of column
     * @param propertyId id of property
     *
     * @return RangeCellFilterComponent that holds both DateFields (smallest and biggest as propertyId) and FilterGroup
     */
    public RangeCellFilterComponent<DateField, HorizontalLayout> setDateFilter(String columnId, String propertyId) {
        return setDateFilter(columnId, null, true);
    }

    /**
     * assign a <b>BetweenFilter</b> to grid for given columnId<br>
     *
     * @param columnId        id of property and column if equal
     * @param dateFormat      the dateFormat to be used for the date fields.
     * @param excludeEndOfDay biggest value until the end of the day (DAY + 23:59:59.999)
     *
     * @return RangeCellFilterComponent that holds both DateFields (smallest and biggest as propertyId) and FilterGroup
     */
    public RangeCellFilterComponent<DateField, HorizontalLayout> setDateFilter(String columnId,
                                                                               java.text.SimpleDateFormat dateFormat,
                                                                               boolean excludeEndOfDay) {
        return setDateFilter(columnId, columnId, dateFormat, excludeEndOfDay);
    }

    /**
     * assign a <b>BetweenFilter</b> to grid for given columnId and propertyId<br>
     *
     * @param columnId        id of column
     * @param propertyId      id of property
     * @param dateFormat      the dateFormat to be used for the date fields.
     * @param excludeEndOfDay biggest value until the end of the day (DAY + 23:59:59.999)
     *
     * @return RangeCellFilterComponent that holds both DateFields (smallest and biggest as propertyId) and FilterGroup
     */
    public RangeCellFilterComponent<DateField, HorizontalLayout> setDateFilter(String columnId,
                                                                               String propertyId,
                                                                               java.text.SimpleDateFormat dateFormat,
                                                                               boolean excludeEndOfDay) {
        final CellFilterId cellFilterId = new CellFilterId(propertySet, columnId, propertyId);
        final Class<?> propertyType = cellFilterId.getPropertyType();
        if (!Date.class.equals(propertyType)) {
            throw new IllegalArgumentException("columnId " + columnId + " is not of type Date");
        }
        final RangeCellFilterComponent<DateField, HorizontalLayout> filter = RangeCellFilterComponentFactory
                .createForDate(
                cellFilterId,
                dateFormat,
                excludeEndOfDay,
                (serFilter, cellFilterId1) -> this.replaceFilter(serFilter, cellFilterId1),
                this::removeFilter);

        handleFilterRow(cellFilterId, filter);
        return filter;
    }

    public static class BooleanRepresentation {

        public static BooleanRepresentation TRUE_VALUE = new BooleanRepresentation(true,
                                                                                   VaadinIcons.CHECK_SQUARE,
                                                                                   Boolean.TRUE.toString());

        public static BooleanRepresentation FALSE_VALUE = new BooleanRepresentation(false,
                                                                                    VaadinIcons.CLOSE,
                                                                                    Boolean.FALSE.toString());

        private boolean value;

        private FontIcon icon;

        private String caption;

        public BooleanRepresentation(Boolean value, FontIcon icon, String caption) {
            this.value = value;
            this.icon = icon;
            this.caption = caption;
        }

        public FontIcon getIcon() {
            return icon;
        }

        public String getCaption() {
            return caption;
        }

        public boolean getValue() {
            return value;
        }
    }

    public class CellFilterId implements Serializable {
        private final String                   columnId;
        private final String                   propertyId;
        private final PropertyDefinition<T, ?> propertyDefinition;

        public CellFilterId(final PropertySet<T> propertySet, final String columnId) {
            this(propertySet, columnId, columnId);
        }

        public CellFilterId(final PropertySet<T> propertySet, final String columnId, final String propertyId) {
            this.columnId = columnId;
            this.propertyId = propertyId;
            Optional<PropertyDefinition<T, ?>> optionalProperty = propertySet.getProperty(propertyId);
            if (!optionalProperty.isPresent()) {
                throw new NoSuchElementException(String.format("propertyId %s not available", propertyId));
            } else {
                propertyDefinition = optionalProperty.get();
            }
        }

        public String getColumnId() {
            return columnId;
        }

        public String getPropertyId() {
            return propertyId;
        }

        public ValueProvider<T, ?> getGetter() {
            return propertyDefinition.getGetter();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CellFilterId that = (CellFilterId) o;

            if (columnId != null ? !columnId.equals(that.columnId) : that.columnId != null) {
                return false;
            }
            return propertyId != null ? propertyId.equals(that.propertyId) : that.propertyId == null;
        }

        @Override
        public int hashCode() {
            int result = columnId != null ? columnId.hashCode() : 0;
            result = 31 * result + (propertyId != null ? propertyId.hashCode() : 0);
            return result;
        }

        public Class<?> getPropertyType() {
            return propertyDefinition.getType();
        }
    }
}
