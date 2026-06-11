package com.arena.cpj.judge0;
 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
 
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Judge0CallbackPayload {
 
    private String token;
 
    private Status status;
 
    private String time;
 
    private Integer memory;
 
    private String stdout;
 
    private String stderr;
 
    @JsonProperty("compile_output")
    private String compileOutput;
 
    private String message;
 
    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
 
        private int id;
 
        @JsonProperty("description")
        private String description;
    }
}
