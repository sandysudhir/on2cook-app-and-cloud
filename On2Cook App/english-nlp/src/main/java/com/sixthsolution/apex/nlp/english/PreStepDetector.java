package com.sixthsolution.apex.nlp.english;

import com.nobigsoftware.dfalex.Pattern;
import com.sixthsolution.apex.nlp.dict.Tag;
import com.sixthsolution.apex.nlp.english.filter.PreStepDetectionFilter;
import com.sixthsolution.apex.nlp.ner.Entity;
import com.sixthsolution.apex.nlp.ner.Label;
import com.sixthsolution.apex.nlp.ner.regex.ChunkDetectionFilter;
import com.sixthsolution.apex.nlp.ner.regex.ChunkDetector;
import com.sixthsolution.apex.nlp.util.Pair;

import java.util.Arrays;
import java.util.List;

import static com.nobigsoftware.dfalex.Pattern.match;
import static com.nobigsoftware.dfalex.Pattern.regex;
import static com.sixthsolution.apex.nlp.ner.Entity.PRESTEP;

/**
 * @author Saeed Masoumi (s-masoumi@live.com)
 */

public class PreStepDetector extends ChunkDetector {
    /**
     * @return at Mall, at home , ...
     */
//    private static Pattern preStep() {
//        return match(Tag.PRESTEP_PREFIX.toString())
//                .thenMaybe(repeat(Tag.NONE.toString()))
//                .thenMaybe(Tag.PRESTEP_SUFFIX.toString());
//    }

//    private static Pattern preStep() {
//        //return match(Tag.PRESTEP_PREFIX.toString()).thenRegex("(.*?)("+Tag.PRESTEP_SUFFIX.toString()+")");
//        return match(Tag.PRESTEP_PREFIX.toString())
//                .thenMaybe(regex("(.*)("+ Tag.PRESTEP_SUFFIX.toString() +")"));
//    }

//    private static Pattern preStepIdentify() {
//        return match(Tag.PRESTEP_PREFIX.toString())
//                .thenMaybe(repeat(Tag.NONE.toString()))
//                .thenMaybe(match(Tag.PRESTEP_SUFFIX.toString()))
//                .thenMaybe(match(Tag.PRESTEP_SEPARATOR.toString()))
//                .thenMaybe(repeat(Tag.NONE.toString()))
//                .thenMaybe(match(Tag.PRESTEP_SUFFIX.toString()))
//                .thenMaybe(match(Tag.PRESTEP_SEPARATOR.toString()))
//                .thenMaybe(repeat(Tag.NONE.toString()))
//                .thenMaybe(match(Tag.PRESTEP_SUFFIX.toString()))
//                .thenMaybe(match(Tag.PRESTEP_SEPARATOR.toString()))
//                .thenMaybe(repeat(Tag.NONE.toString()))
//                .thenMaybe(match(Tag.PRESTEP_SUFFIX.toString()))
//                .thenMaybe(match(Tag.PRESTEP_SEPARATOR.toString()))
//                .thenMaybe(repeat(Tag.NONE.toString()))
//                .then(match(Tag.PRESTEP_SUFFIX.toString()));
//    }

    private static Pattern preStepIdentify() {
        return match(Tag.PRESTEP_PREFIX.toString())
                .thenRegex(".*")
                .then(match(Tag.PRESTEP_SUFFIX.toString()));
    }

    private static Pattern preStepIdentify2() {
        return regex("("+ Tag.PRESTEP_PREFIX +").*("+ Tag.PRESTEP_SUFFIX +")");
                //.then(match(Tag.PRESTEP_SUFFIX.toString()));
    }

    @Override
    protected List<Pair<Label, Pattern>> getPatterns() {
        return Arrays.asList(
                //newPattern(Label.PRESTEP, preStep())
                //newPattern(Label.PRESTEP, preStepIdentify())
                newPattern(Label.PRESTEP, preStepIdentify2())
        );
    }

    @Override
    protected List<? extends ChunkDetectionFilter> getFilters() {
        return Arrays.asList(new PreStepDetectionFilter());
    }

    @Override
    protected Entity getEntity() {
        return PRESTEP;
    }
}
