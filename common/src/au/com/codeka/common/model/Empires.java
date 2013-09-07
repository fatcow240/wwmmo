// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: ./messages.proto
package au.com.codeka.common.model;

import com.squareup.wire.Message;
import com.squareup.wire.ProtoField;
import java.util.Collections;
import java.util.List;

import static com.squareup.wire.Message.Label.REPEATED;

public final class Empires extends Message {

  public static final List<Empire> DEFAULT_EMPIRES = Collections.emptyList();

  @ProtoField(tag = 1, label = REPEATED)
  public List<Empire> empires;

  private Empires(Builder builder) {
    super(builder);
    this.empires = copyOf(builder.empires);
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof Empires)) return false;
    return equals(empires, ((Empires) other).empires);
  }

  @Override
  public int hashCode() {
    int result = hashCode;
    return result != 0 ? result : (hashCode = empires != null ? empires.hashCode() : 0);
  }

  public static final class Builder extends Message.Builder<Empires> {

    public List<Empire> empires;

    public Builder() {
    }

    public Builder(Empires message) {
      super(message);
      if (message == null) return;
      this.empires = copyOf(message.empires);
    }

    public Builder empires(List<Empire> empires) {
      this.empires = empires;
      return this;
    }

    @Override
    public Empires build() {
      return new Empires(this);
    }
  }
}
