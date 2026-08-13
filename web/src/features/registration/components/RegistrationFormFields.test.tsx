import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { RegistrationForm } from "../../../types/registrationForm";
import { RegistrationFormFields } from "./RegistrationFormFields";
import { validateRegistrationAnswers } from "../validateRegistrationAnswers";

const form: RegistrationForm = {
  fields: [
    {
      type: "SHORT_TEXT",
      fieldId: "name",
      label: "Team name",
      helpText: null,
      required: true,
      maxLength: 12,
    },
    {
      type: "LONG_TEXT",
      fieldId: "idea",
      label: "Project idea",
      helpText: "Tell us what you want to build",
      required: false,
      maxLength: 200,
    },
    {
      type: "SINGLE_CHOICE",
      fieldId: "level",
      label: "Experience",
      helpText: "Choose one",
      required: true,
      options: ["Beginner", "Advanced"],
    },
    {
      type: "MULTIPLE_CHOICE",
      fieldId: "topics",
      label: "Topics",
      helpText: "Choose any",
      required: true,
      options: ["AI", "Robotics"],
    },
    {
      type: "NUMBER",
      fieldId: "teamSize",
      label: "Team size",
      helpText: "Between 1 and 5",
      required: true,
      minimum: 1,
      maximum: 5,
    },
  ],
};

describe("RegistrationFormFields", () => {
  it("renders every field kind in definition order and emits typed answers", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();

    render(<RegistrationFormFields form={form} answers={{ topics: ["AI"] }} onAnswer={onAnswer} />);

    const teamName = screen.getByLabelText("Team name *");
    const projectIdea = screen.getByLabelText("Project idea");
    const experience = screen.getByRole("radiogroup", { name: "Experience *" });
    const topics = screen.getByRole("group", { name: "Topics *" });
    const teamSize = screen.getByLabelText("Team size *");
    expect(teamName).toAppearBefore(projectIdea);
    expect(projectIdea).toAppearBefore(experience);
    expect(experience).toAppearBefore(topics);
    expect(topics).toAppearBefore(teamSize);
    expect(teamSize).toHaveAttribute("step", "any");
    expect(screen.queryByText("Keep it short")).not.toBeInTheDocument();

    await user.type(teamName, "R");
    await user.click(screen.getByLabelText("Advanced"));
    await user.click(screen.getByLabelText("Robotics"));
    await user.type(teamSize, "3");

    expect(onAnswer).toHaveBeenCalledWith("name", "R");
    expect(onAnswer).toHaveBeenCalledWith("level", "Advanced");
    expect(onAnswer).toHaveBeenCalledWith("topics", ["AI", "Robotics"]);
    expect(onAnswer).toHaveBeenCalledWith("teamSize", 3);
  });

  it("reports required and constraint failures by stable field id", () => {
    expect(
      validateRegistrationAnswers(form, {
        name: "A name that is much too long",
        level: "Not defined",
        topics: [],
        teamSize: 9,
      }),
    ).toEqual({
      name: "Use 12 characters or fewer.",
      level: "Choose one of the available options.",
      topics: "Required.",
      teamSize: "Enter a number no greater than 5.",
    });
  });

  it("renders a server field error beside its field", () => {
    render(
      <RegistrationFormFields
        form={form}
        answers={{}}
        fieldErrors={{ name: "This answer was rejected by the server." }}
        onAnswer={vi.fn()}
      />,
    );

    expect(screen.getByText("This answer was rejected by the server.")).toHaveAttribute("role", "alert");
  });

  it("removes a multiple choice and clears a number without losing their value types", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();
    render(
      <RegistrationFormFields
        form={form}
        answers={{ topics: ["AI", "Robotics"], teamSize: 3 }}
        onAnswer={onAnswer}
      />,
    );

    await user.click(screen.getByLabelText("AI"));
    await user.clear(screen.getByLabelText("Team size *"));

    expect(onAnswer).toHaveBeenCalledWith("topics", ["Robotics"]);
    expect(onAnswer).toHaveBeenCalledWith("teamSize", "");
  });

  it("accepts valid answers and checks the remaining type and range branches", () => {
    expect(
      validateRegistrationAnswers(form, {
        name: "Robots",
        idea: "A helpful robot",
        level: "Beginner",
        topics: ["AI"],
        teamSize: 3,
      }),
    ).toEqual({});

    expect(
      validateRegistrationAnswers(form, {
        name: 4,
        level: "Beginner",
        topics: ["Unknown"],
        teamSize: 0,
      }),
    ).toEqual({
      name: "Enter text.",
      topics: "Choose only available options.",
      teamSize: "Enter a number no less than 1.",
    });

    expect(
      validateRegistrationAnswers(form, {
        name: "Robots",
        level: "Beginner",
        topics: "AI",
        teamSize: "many",
      }),
    ).toEqual({
      topics: "Choose only available options.",
      teamSize: "Enter a number.",
    });
  });
});
