/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.forward;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.output.MLOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
@ToString
public class MLForwardResponse extends ActionResponse implements ToXContentObject {

    String status;
    MLOutput mlOutput;

    @Builder
    public MLForwardResponse(String status, MLOutput mlOutput) {
        this.status = status;
        this.mlOutput = mlOutput;
    }


    public MLForwardResponse(StreamInput in) throws IOException {
        super(in);
        status = in.readOptionalString();
        if (in.readBoolean()) {
            mlOutput = MLOutput.fromStream(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(status);
        if (mlOutput != null) {
            out.writeBoolean(true);
            mlOutput.writeTo(out);
        } else {
            out.writeBoolean(false);
        }

    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("result", mlOutput);
        builder.endObject();
        return builder;
    }

    public static MLForwardResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLForwardResponse) {
            return (MLForwardResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLForwardResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLForwardResponse", e);
        }
    }
}
