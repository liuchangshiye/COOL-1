package com.nus.cool.core.io.readstore;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Maps;
import com.nus.cool.core.io.storevector.InputVector;
import com.nus.cool.core.io.storevector.InputVectorFactory;
import com.nus.cool.core.io.storevector.LZ4InputVector;
import com.nus.cool.core.schema.FieldType;
import com.rabinhash.RabinHashFunction32;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

public class UserMetaFieldRS implements MetaFieldRS{
    private static final RabinHashFunction32 rhash = RabinHashFunction32.DEFAULT_HASH_FUNCTION;

    private Charset charset;
    List<Integer> invariantFields = new ArrayList<>();
    Map<String, Integer> invariantName2Id = Maps.newHashMap();
    Map<String, Integer> invariantId2Name = Maps.newHashMap();

    private List<Integer> userFieldHashSize;
    private FieldType fieldType;

    private InputVector fingerVec;

    private InputVector globalIDVec;

    @Getter
    private List<InputVector> userToInvariant;

    private InputVector valueVec;

    // inverse map from global id to the offset in values.
    //  only populated once when getString is called to retrieve from valueVec
    private Map<Integer, Integer> id2offset;

    public UserMetaFieldRS(Charset charset, List<Integer> invariantFields, Map<String, Integer> invariantName2Id) {
        this.charset = checkNotNull(charset);
        this.invariantFields=invariantFields;
        this.invariantName2Id=invariantName2Id;
    }

    @Override
    public FieldType getFieldType() {
        return this.fieldType;
    }

    @Override
    public int find(String key) {
        int globalIDIdx = this.fingerVec.find(rhash.hash(key));
        return this.globalIDVec.get(globalIDIdx);
    }

    public int find(int hash) {
        return this.fingerVec.find(hash);
    }

    public int findInvariantHash(int globalID) {
        int fingerIdx = this.globalIDVec.find(globalID);
        return this.fingerVec.get(fingerIdx);
    }

    @Override
    public int count() {
        return this.fingerVec.size();
    }

    @Override
    public String getString(int i) {
        if (this.id2offset == null) {
            this.id2offset = Maps.newHashMap();
            // lazily populate the inverse index only once
            for (int j = 0; j < this.globalIDVec.size(); j++) {
                this.id2offset.put(this.globalIDVec.get(j), j);
            }
        }
        return ((LZ4InputVector) this.valueVec)
                .getString(this.id2offset.get(i), this.charset);
    }

    @Override
    public int getMaxValue() {
        return this.count() - 1;
    }

    @Override
    public int getMinValue() {
        return 0;
    }

    @Override
    public void readFromWithFieldType(ByteBuffer buffer, FieldType fieldType) {
        this.fieldType = fieldType;
        this.fingerVec = InputVectorFactory.readFrom(buffer);
        this.globalIDVec = InputVectorFactory.readFrom(buffer);
        this.userToInvariant=new ArrayList<>(this.invariantName2Id.size());
        for(int i=0;i<this.invariantName2Id.size();i++)
        {
            this.userToInvariant.set(i,InputVectorFactory.readFrom(buffer));
        }
        this.valueVec = InputVectorFactory.readFrom(buffer);
    }

    @Override
    public void readFrom(ByteBuffer buffer) {
        FieldType fieldType = FieldType.fromInteger(buffer.get());
        this.readFromWithFieldType(buffer, fieldType);
    }
}