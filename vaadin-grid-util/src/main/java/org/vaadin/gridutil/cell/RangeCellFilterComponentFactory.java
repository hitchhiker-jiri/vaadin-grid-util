package org.vaadin.gridutil.cell;

import com.vaadin.data.Converter;
import com.vaadin.data.converter.LocalDateToDateConverter;
import com.vaadin.server.SerializablePredicate;
import com.vaadin.ui.DateField;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.TextField;
import org.vaadin.gridutil.cell.filter.BetweenFilter;
import org.vaadin.gridutil.cell.filter.EqualFilter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by georg.hicker on 03.08.2017.
 */
public class RangeCellFilterComponentFactory {

    public static <T extends Number & Comparable<? super T>> RangeCellFilterComponentTyped<T, TextField,
            HorizontalLayout> createForNumberType(
            final GridCellFilter.CellFilterId cellFilterId,
            Class<T> propertyType,
            String converterErrorMessage,
            String smallestInputPrompt,
            String biggestInputPrompt,
            BiConsumer<SerializablePredicate<T>, GridCellFilter.CellFilterId> filterReplaceConsumer,
            Consumer<GridCellFilter.CellFilterId> filterRemoveConsumer) {
        if (Integer.class.equals(propertyType) || Long.class.equals(propertyType) || Double.class.equals
                (propertyType) || Float.class
                .equals(propertyType) || BigInteger.class.equals(propertyType) || BigDecimal.class.equals
                (propertyType)) {
            return new RangeCellFilterComponentTyped<T, TextField, HorizontalLayout>() {
                private TextField smallest, biggest;

                @Override
                public TextField getSmallestField() {
                    if (smallest == null) {
                        smallest = genNumberField(SMALLEST,
                                                  NumberUtil.getConverter(propertyType, converterErrorMessage),
                                                  smallestInputPrompt);
                    }
                    return smallest;
                }

                @Override
                public TextField getBiggestField() {
                    if (biggest == null) {
                        biggest = genNumberField(BIGGEST,
                                                 NumberUtil.getConverter(propertyType, converterErrorMessage),
                                                 biggestInputPrompt);
                    }
                    return biggest;
                }

                private TextField genNumberField(final String propertyId,
                                                 final Converter converter,
                                                 final String inputPrompt) {
                    return FieldFactory.genNumberField(getBinder(), propertyId, converter, inputPrompt);
                }

                @Override
                public HorizontalLayout layoutComponent() {
                    getHLayout().addComponent(getSmallestField());
                    getHLayout().addComponent(getBiggestField());
                    getHLayout().setExpandRatio(getSmallestField(), 1);
                    getHLayout().setExpandRatio(getBiggestField(), 1);

                    initBinderValueChangeHandler();

                    return getHLayout();
                }

                private void initBinderValueChangeHandler() {
                    getBinder().addValueChangeListener(e -> {
                        final T smallest = getBinder().getBean().getSmallest();
                        final T biggest = getBinder().getBean().getBiggest();
                        if (smallest != null || biggest != null) {
                            if (smallest != null && biggest != null && smallest.equals(biggest)) {
                                filterReplaceConsumer.accept(new EqualFilter(smallest), cellFilterId);
                            } else {
                                filterReplaceConsumer.accept(new BetweenFilter((smallest != null ?
                                                                                smallest :
                                                                                NumberUtil.getBoundaryValue
                                                                                        (propertyType,
                                                                                                            false)),
                                                                               (biggest != null ?
                                                                                biggest :
                                                                                NumberUtil.getBoundaryValue
                                                                                        (propertyType,
                                                                                                            true))),
                                                             cellFilterId);
                            }
                        } else {
                            filterRemoveConsumer.accept(cellFilterId);
                        }

                    });
                }

                private T checkObject(Object value) {
                    if (value != null && value.getClass().equals(propertyType)) {
                        return propertyType.cast(value);
                    }
                    return null;
                }

                @Override
                public void clearFilter() {
                    getBinder().setBean(new TwoValueObjectTyped<T>());
                }
            };
        }
        return null;
    }

    private static Date MIN_DATE_VALUE = new Date(0); // 1970-01-01 00:00:00

    private static Date MAX_DATE_VALUE = new Date(32503676399000L); // 2999-12-31 23:59:59

    public static RangeCellFilterComponent<DateField, HorizontalLayout> createForDate(final GridCellFilter
            .CellFilterId cellFilterId,
                                                                                      final java.text
                                                                                              .SimpleDateFormat
                                                                                              dateFormat,
                                                                                      final boolean excludeEndOfDay,
                                                                                      BiConsumer<SerializablePredicate<Date>, GridCellFilter.CellFilterId> filterReplaceConsumer,
                                                                                      Consumer<GridCellFilter
                                                                                              .CellFilterId>
                                                                                              filterRemoveConsumer) {
        return new RangeCellFilterComponent<DateField, HorizontalLayout>() {

            private final LocalDateToDateConverter ldToDateConverter = new LocalDateToDateConverter();

            private DateField smallest;

            private DateField biggest;

            @Override
            public DateField getSmallestField() {
                if (smallest == null) {
                    smallest = genDateField(SMALLEST, dateFormat);
                }
                return smallest;
            }

            @Override
            public DateField getBiggestField() {
                if (biggest == null) {
                    biggest = genDateField(BIGGEST, dateFormat);
                }
                return biggest;
            }

            private DateField genDateField(final String propertyId, final SimpleDateFormat dateFormat) {
                return FieldFactory.genDateField(getBinder(), propertyId, dateFormat);
            }

            @Override
            public HorizontalLayout layoutComponent() {
                getHLayout().addComponent(getSmallestField());
                getHLayout().addComponent(getBiggestField());
                getHLayout().setExpandRatio(getSmallestField(), 1);
                getHLayout().setExpandRatio(getBiggestField(), 1);

                initBinderValueChangeHandler();

                return getHLayout();
            }

            private Date fixTiming(Date date, boolean excludeEndOfDay) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                calendar.set(Calendar.MILLISECOND, excludeEndOfDay ? 0 : 999);
                calendar.set(Calendar.SECOND, excludeEndOfDay ? 0 : 59);
                calendar.set(Calendar.MINUTE, excludeEndOfDay ? 0 : 59);
                calendar.set(Calendar.HOUR, excludeEndOfDay ? 0 : 23);
                return calendar.getTime();
            }

            private void initBinderValueChangeHandler() {
                getBinder().addValueChangeListener(e -> {
                    Object smallest = getBinder().getBean().getSmallest();
                    Object biggest = getBinder().getBean().getBiggest();
                    Date smallestDate = checkObject(smallest);
                    Date biggestDate = checkObject(biggest);
                    if (this.smallest != null || biggest != null) {
                        if (this.smallest != null && biggest != null && this.smallest.equals(biggest)) {
                            filterReplaceConsumer.accept(new EqualFilter(this.smallest), cellFilterId);
                        } else {
                            filterReplaceConsumer.accept(new BetweenFilter(smallestDate != null ?
                                                            fixTiming(smallestDate, true) :
                                                            MIN_DATE_VALUE,
                                                            biggestDate != null ?
                                                            fixTiming(biggestDate, excludeEndOfDay) :
                                                            MAX_DATE_VALUE), cellFilterId);
                        }
                    } else {
                        filterRemoveConsumer.accept(cellFilterId);
                    }
                });
            }

            private Date checkObject(Object value) {
                if (value instanceof LocalDate) {
                    return ldToDateConverter.convertToModel((LocalDate) value, null)
                                            .getOrThrow(msg -> new IllegalArgumentException(msg));
                } else if (value instanceof Date) {
                    return (Date) value;
                }
                return null;
            }

            @Override
            public void clearFilter() {
                getBinder().setBean(new TwoValueObject());
            }
        };
    }
}